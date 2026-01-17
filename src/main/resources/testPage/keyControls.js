window.addEventListener("load", function () {
    // Let k listen for keydown
    window.addEventListener("keydown", function (event) {
        switch(event.key){
            case "s":
            case "S": openFolder.click(); break;
            case "g":
            case "G": document.getElementById("gm").checked = true; break;
            case "h":
            case "H": document.getElementById("ov").checked = true; break;
            case "j":
            case "J": document.getElementById("pv").checked = true; break;
            case "o":
            case "O": winBtn.click(); break;
            case "r":
            case "R": reload.click(); break;
            case "d":
            case "D": showData.click(); break;
            case "[": document.getElementById("prev").click(); break;
            case "]": document.getElementById("next").click(); break;
        };
    });
});