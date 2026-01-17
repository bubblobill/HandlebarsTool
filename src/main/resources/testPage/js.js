//establish Server-Sent Events connection
var evtSource = new EventSource("/sse");
//function reconnect(){
//    if(evtSource.readyState == 2){
//        evtSource = new EventSource("/sse");
//        setTimeout(reconnect, 1500);
//    }
//}
//evtSource.onerror = (event) => {
//    console.error("EventSource failed: retry");
//    reconnect();
// };
// Server-Side Events, i.e. updates
evtSource.onmessage = (event) => {
    console.log("refresh notification -> event.data: " + event.data);
    const data = JSON.parse(event.data);
    if(data["idle"]){
    } else if(data["template-change"]){
        reloadPage();
    } else if(data["source-change"]){
        reloadSheet();
    }
};



const winBtn = document.getElementById("separate");
const reloadBtn = document.getElementById("reload");
const link = document.getElementById("newWindow");
const ss = document.getElementById("overlay");
const sheetSelect = document.getElementById("sheet");
const showData = document.getElementById("showData");
const bg = document.getElementById("bg");
const submitElements = document.querySelectorAll(".submit-on-change");
const openFolder = document.getElementById("openFolder");
window.addEventListener("load", function () {
    for(const arrow of document.querySelectorAll(".radio-label")){ arrow.innerHTML = "&#x27A4;"; }

    sheetSelect.addEventListener("change", function(e){
        if(sheetSelect.value != null && sheetSelect.value != undefined && sheetSelect.value != "null" && sheetSelect.value != ""){
            ss.src = "http://localhost:" + window.location.port + "/sheet/" + sheetSelect.value;
            link.href = ss.src + "?s";
        } else {
            ss.src = "http://localhost:" + window.location.port + "/sheet/_default.hbs";
            }
    });

    bg.addEventListener("change", function () {
        document.body.style.backgroundImage = "url(" + bg.options[bg.selectedIndex].value + ")";
    });

    for(const el of submitElements){
        if(el.type == "submit"){
            el.addEventListener("click",function(evt){
                document.getElementById("cycle").value = evt.target.value;
                submitForm(el.form);
            });
        } else {
            el.addEventListener("change",function(){
                submitForm(el.form);
            });
        }
    }

    if(sheetSelect.value == undefined){ sheetSelect.selectedIndex = -1; }

    openFolder.addEventListener("click",function(){ submitForm(document.forms["fm_folder"]); });
    showData.addEventListener("click", copyData);

    reload.addEventListener("click", reloadSheet);

    winBtn.addEventListener("click", function (e) { link.click(); });

    var evt = document.createEvent('HTMLEvents');
    evt.initEvent("change", false, false);
    sheetSelect.dispatchEvent(evt);
});

function reloadSheet(){
    ss.src = ss.src;
}

function reloadPage(){
    window.location.href = `${window.location.href.split('?')[0]}?noCache=${new Date().getTime()}`;
}
function copyData(){
    let text = document.getElementById("data").innerText;
    navigator.clipboard.writeText(text);
    alert("Data copied to clipboard");
}
function submitForm(fm){
    var data = new FormData(document.forms[fm.id]);
    const out = {};
    for (const p of data){
        out[p[0]] = p[1];
    }
    fetch(fm.action, { method: "post", body: JSON.stringify(out) });
    setTimeout(reloadSheet, 10);
}