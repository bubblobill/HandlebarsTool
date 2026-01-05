//establish Server-Sent Events connection
const evtSource = new EventSource("/sse");
// Server-Side Events, i.e. updates
evtSource.onmessage = (event) => {
    console.log("refresh notification")
    console.log(event.data);
    reloadPage();
};

window.addEventListener("load", function () {
    const winBtn = document.getElementById("separate");
    const reloadBtn = document.getElementById("reload");
    const link = document.getElementById("newWindow");
    const ss = document.getElementById("overlay");
    const sel = document.getElementById("sheet");
    const showData = document.getElementById("showData");
    const dataPop = document.getElementById("data");
    dataPop.addEventListener("click", function(e){
        showData.checked = false;
    })

    sel.addEventListener("change", function(e){
        if(sel.value != null && sel.value != undefined && sel.value != "null"){
            ss.src = "http://localhost:6781/" + sel.value;
            link.href = ss.src;
        }
    });
    if(sel.value == undefined){
        sel.selectedIndex = 0;
    }

    const bg = document.getElementById("bg");
    bg.addEventListener("change", function () {
        document.body.style.backgroundImage = "url(" + bg.options[bg.selectedIndex].value + ")";
    });

    const submitElements = document.querySelectorAll(".submit-on-change");
    for(const el of submitElements){
        el.addEventListener("change",function(){
            submitForm(el.form);
        });
    }

    reload.addEventListener("click", reloadSheet);

    function reloadSheet(){
        ss.src = ss.src;
    }

    winBtn.addEventListener("click", function (e) {
        link.click();
    });

    function submitForm(fm){
        var data = new FormData(document.forms[fm.id]);
        const out = {};
        for (const p of data){
            out[p[0]] = p[1];
        }
        //Use fetch syntax to submit the form data
        fetch(fm.action, { method: "post", body: JSON.stringify(out) });
        setTimeout(reloadSheet, 10);
    }

    var evt = document.createEvent('HTMLEvents');
    evt.initEvent("change", false, false);
    sel.dispatchEvent(evt);
});
function reloadPage(){
    window.location.href = `${window.location.href.split('?')[0]}?noCache=${new Date().getTime()}`;
}