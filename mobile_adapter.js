(function() {
    // ١. زیادکردنی تاگی meta viewport ئەگەر لە وێبسایتەکەدا نەبوو یان قەبارەکەی هی کۆمپیوتەر بوو
    var viewport = document.querySelector('meta[name="viewport"]');
    if (!viewport) {
        viewport = document.createElement('meta');
        viewport.name = 'viewport';
        document.head.appendChild(viewport);
    }
    viewport.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes');

    // ٢. چارەسەرکردنی هەر بەشێک کە پانییەکەی بە پیکسڵی جێگیر (Fixed pixel width) لە شاشەی مۆبایل گەورەتر بێت
    function fixFixedWidthElements() {
        var screenWidth = window.innerWidth || document.documentElement.clientWidth;
        var allElements = document.body ? document.body.getElementsByTagName('*') : [];
        for (var i = 0; i < allElements.length; i++) {
            var el = allElements[i];
            if (el.tagName === 'TABLE' || el.tagName === 'TR' || el.tagName === 'TD' || el.tagName === 'TH') {
                continue;
            }
            var rect = el.getBoundingClientRect();
            if (rect.width > screenWidth + 4) {
                el.style.maxWidth = '100%';
                el.style.width = 'auto';
                el.style.overflowX = 'auto';
            }
        }
    }

    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        fixFixedWidthElements();
    } else {
        document.addEventListener('DOMContentLoaded', fixFixedWidthElements);
    }
    window.addEventListener('resize', fixFixedWidthElements);
})();
