/* 注入脚本 —— 仿原版 FusionApp 的 onInjectionJavaScript 那一套：
 * 只做三件事，绝不改网页的布局与配色（用户要求：界面就用网页自己的）：
 *   1. 长按元素 → 高亮 → 交给原生弹系统菜单（原版的「元素审查」）
 *   2. 屏蔽元素（原版的「屏蔽图片/链接广告」）→ 规则由原生存，下次加载自动隐藏
 *   3. 暴露一个极小的窗口函数，方便原生随时补注（幂等，重复注入无副作用）
 *
 * 注入时机：onPageStarted 一次（尽早挂事件）+ onPageFinished 一次（DOM 就绪后补样式）。
 */
(function () {
  'use strict';
  if (window.__GJ_INJ) { try { window.__GJ_APPLY && window.__GJ_APPLY(); } catch (e) { } return; }
  window.__GJ_INJ = 1;

  // 长按审查已按用户要求关闭：不再监听 contextmenu、不再画高亮框。
  // （屏蔽元素的能力改由「长按顶栏城市名」的原生菜单进入，见 MainActivity）
  window.__GJ_CLEAR = function () { };

  // ---------- 主题（格纸覆盖样式，由原生把 theme.css 文本传进来） ----------

  window.__GJ_THEME = function (css, on) {
    var id = '__gj_theme';
    var st = document.getElementById(id);
    if (!on) {
      if (st) st.textContent = '';
      document.body && document.body.classList.remove('gj-theme');
      return;
    }
    if (!st) {
      st = document.createElement('style');
      st.id = id;
      (document.head || document.documentElement).appendChild(st);
    }
    st.textContent = css || '';
    document.body && document.body.classList.add('gj-theme');
    polish();
  };

  // ---------- 按文本隐藏（有些条没有稳定的类名，只有文案是稳定的） ----------

  var HIDE_TEXT = ['输入目的站'];
  var HIDDEN_FLAG = 'data-gj-hid';

  function hideByText(list) {
    if (!document.body || !list || !list.length) return;
    var all = document.body.querySelectorAll('div,span,a,li,section');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el.getAttribute(HIDDEN_FLAG)) continue;
      // 只保护**路线规划页**：那一页的起终点行不能被动（它们也是 div，文案可能撞上「输入目的站」）。
      // 站点详情页那条「输入目的站」横条**仍然要隐藏** —— 它也是输入区，所以不能按"含输入就跳过"来判断。
      if (el.closest && el.closest('.page-transfer')) continue;
      var t = (el.textContent || '').replace(/\s+/g, '').trim();
      if (t.length === 0 || t.length > 12) continue;      // 只命中短文案，避免把整页干掉
      for (var k = 0; k < list.length; k++) {
        if (t === list[k]) {
          // 往上找一层"像条子"的容器（高度 < 120px 才收），否则只隐藏自己
          var box = el;
          for (var up = 0; up < 3 && box.parentElement; up++) {
            var h = box.getBoundingClientRect ? box.getBoundingClientRect().height : 0;
            if (h > 0 && h < 120) break;
            box = box.parentElement;
          }
          box.style.display = 'none';
          box.setAttribute(HIDDEN_FLAG, '1');
          break;
        }
      }
    }
  }

  // ---------- 小润色：给 ≤5 分钟的数字上主色（CSS 不知道数值，只能这里加类） ----------

  var polishTimer = null;

  function polish() {
    if (!document.body || !document.body.classList.contains('gj-theme')) return;
    try { hideByText(HIDE_TEXT); } catch (e) { }
    var nodes = document.querySelectorAll('.line-time em.f-24');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var t = (el.textContent || '').replace(/\s+/g, '');
      var m = /^(\d{1,2})$/.exec(t.replace(/分|秒/g, ''));
      var urgent = !!m && parseInt(m[1], 10) <= 5;
      if (urgent && !/gj-urgent/.test(el.className)) el.className += ' gj-urgent';
      else if (!urgent && /gj-urgent/.test(el.className)) {
        el.className = el.className.replace(/\s?gj-urgent/, '');
      }
    }
  }

  function schedulePolish() {
    if (polishTimer) return;
    polishTimer = setTimeout(function () {
      polishTimer = null;
      try { polish(); } catch (e) { }
    }, 220);
  }

  // 页面是 Angular 的，列表会重渲染 —— 变化了就重新润一次（节流）
  try {
    new MutationObserver(schedulePolish).observe(document.documentElement, {
      childList: true, subtree: true
    });
  } catch (e) { }
  window.addEventListener('load', schedulePolish, false);

  // 用户点城市（网页城市页的 .table-item）：立刻告诉原生"已选过"，别再来回跳
  document.addEventListener('click', function (e) {
    var t = e.target;
    if (!t || !t.closest) return;
    if (t.closest('.table-item') || t.closest('.city-table-container')) {
      try { GJ.cityPicked(); } catch (x) { }
    }
  }, true);

  // 把网页当前城市名报给原生（原生顶栏的城市字要跟着网页走）
  function reportCity() {
    try {
      var el = document.querySelector('.switch-city .text')
        || document.querySelector('.switch-city-link .text')
        || document.querySelector('.switch-city .text-ellipsis');
      if (el) {
        var t = (el.textContent || '').replace(/\s+/g, '');
        if (t.length > 0 && t.length <= 8) GJ.cityName(t);
      }
    } catch (e) { }
  }

  // 把路由变化报给原生（首次启动要靠它判断"城市选完了没"）
  function reportHash() {
    try { GJ.hash(String(location.hash || '')); } catch (e) { }
  }
  window.addEventListener('hashchange', function () { reportHash(); reportCity(); }, false);
  setTimeout(function () { reportHash(); reportCity(); }, 600);
  setTimeout(reportCity, 2000);

  // —— 下面这些是给「不接原生也能用」准备的：在浏览器里调试时，屏蔽规则走 localStorage
  window.__GJ_LOCAL = {
    shield: function (sel) {
      try { GJ.shield(sel); } catch (e) { }
    }
  };
})();
