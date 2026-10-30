(function () {
    // 常见编程语言关键字（大小写敏感，SQL 大写关键字单独匹配）
    var KW = /^(?:if|else|elif|for|while|do|return|break|continue|switch|case|default|int|long|float|double|char|void|bool|unsigned|signed|struct|enum|typedef|static|const|constexpr|final|public|private|protected|virtual|override|class|interface|extends|implements|new|delete|this|self|try|catch|except|finally|throw|raise|import|from|package|using|namespace|template|typename|sizeof|null|nullptr|true|false|None|True|False|def|function|var|let|async|await|yield|print|SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|JOIN|GROUP|ORDER|BY)$/;
    var re = /("(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*')|(\/\/[^\n]*|\/\*[\s\S]*?\*\/|#[ \t][^\n]*|--[^\n]*)|\b([A-Za-z_][A-Za-z0-9_]*)\b|\b(\d[\d._]*(?:e[+-]?\d+)?)\b/g;
    function esc(s) {
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
    var blocks = document.querySelectorAll('pre code');
    for (var i = 0; i < blocks.length; i++) {
        var el = blocks[i];
        var code = el.textContent, out = '', last = 0, m;
        re.lastIndex = 0;
        while ((m = re.exec(code))) {
            out += esc(code.slice(last, m.index));
            var cls = '';
            if (m[1]) { cls = 'hs'; }
            else if (m[2]) { cls = 'hc'; }
            else if (m[3]) {
                if (KW.test(m[3])) { cls = 'hk'; }
                else if (/^\(/.test(code.slice(re.lastIndex))) { cls = 'hf'; }
            } else if (m[4]) { cls = 'hn'; }
            out += cls ? '<span class="' + cls + '">' + esc(m[0]) + '</span>' : esc(m[0]);
            last = re.lastIndex;
        }
        out += esc(code.slice(last));
        el.innerHTML = out;
    }
})();
