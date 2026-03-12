
// Let k listen for keydown
window.addEventListener("keydown", function (event) {
        switch(event.key){
            case "e": openFolder.click(); break;
            case "g": document.getElementById("gm").checked = true; fireEvent(document.getElementById("gm")); break;
            case "h": document.getElementById("ov").checked = true; fireEvent(document.getElementById("ov")); break;
            case "j": document.getElementById("pv").checked = true; fireEvent(document.getElementById("pv")); break;
            case "n": document.getElementById("npc").checked = true; fireEvent(document.getElementById("npc")); break;
            case "m": document.getElementById("pc").checked = true; fireEvent(document.getElementById("pc")); break;
            case "o": winBtn.click(); break;
            case "r": reload.click(); break;
            case "d": showData.click(); break;
            case "1": document.getElementById("radio-tl").click(); break;
            case "2": document.getElementById("radio-t").click(); break;
            case "3": document.getElementById("radio-tr").click(); break;
            case "4": document.getElementById("radio-l").click(); break;
            case "6": document.getElementById("radio-r").click(); break;
            case "7": document.getElementById("radio-bl").click(); break;
            case "8": document.getElementById("radio-b").click(); break;
            case "9": document.getElementById("radio-br").click(); break;
            case "[": incrementSelect("sheet", -1); break;
            case "]": incrementSelect("sheet", 1); break;
            case "q": incrementSelect("currentTokenImage", -1); break;
            case "w": incrementSelect("currentTokenImage", 1); break;
            case "a": incrementSelect("currentPropertyName", -1); break;
            case "s": incrementSelect("currentPropertyName", 1); break;
            case "z": incrementSelect("bg", -1); break;
            case "x": incrementSelect("bg", 1); break;
            case "t": incrementSelect("theme", -1); break;
            case "y": incrementSelect("theme", 1); break;

    }
});
function incrementSelect(elId, amount){
    const el = document.getElementById(elId);
    const opt = Array.from(el.options);
    let idx = el.selectedIndex + amount;
    idx = idx < 0 ? opt.length + idx: idx >= opt.length ? idx - opt.length : idx;
    opt[idx].selected = true;
    opt[idx].checked = true;
    el.selectedIndex = idx;
    fireEvent(el);
}
function fireEvent(el){
    const evt = document.createEvent('HTMLEvents');
    evt.initEvent("change", false, false);
    el.dispatchEvent(evt);
}