document.querySelectorAll('.tabs').forEach(function(t){var btns=t.querySelectorAll('button');
 function show(id){btns.forEach(function(b){b.setAttribute('aria-selected',b.dataset.p===id)});document.querySelectorAll('.prop').forEach(function(p){p.classList.toggle('on',p.id===id)});try{history.replaceState(null,'','#'+id)}catch(e){}}
 btns.forEach(function(b){b.onclick=function(){show(b.dataset.p)}});
 var h=(location.hash||'').slice(1);show(h&&document.getElementById(h)?h:btns[0].dataset.p)});
