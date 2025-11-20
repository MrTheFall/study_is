(() => {
  async function reloadTable() {
    const container = document.getElementById('org-table');
    if (!container) return;
    const qs = window.location.search || '';
    const bust = (qs && qs.length > 0) ? `${qs}&_rt=${Date.now()}` : `?_rt=${Date.now()}`;
    try {
      const resp = await fetch(`/organizations/table${bust}`, {
        headers: { 'X-Requested-With': 'fetch', 'Cache-Control': 'no-cache' },
        cache: 'no-store'
      });
      if (resp.ok) {
        container.innerHTML = await resp.text();
      } else {
        window.location.reload();
      }
    } catch (e) {
      console.warn('Failed to reload table', e);
      window.location.reload();
    }
  }

  let reloadTmr = null;
  function scheduleReload() {
    if (reloadTmr) clearTimeout(reloadTmr);
    reloadTmr = setTimeout(() => { reloadTmr = null; reloadTable(); }, 150);
  }

  function setRequired(id, required) {
    const el = document.getElementById(id);
    if (!el) return;
    if (required) el.setAttribute('required', 'required'); else el.removeAttribute('required');
  }

  function attachValidity(id, msg) {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('invalid', () => {
      if (el.hasAttribute('required')) el.setCustomValidity(msg);
    });
    el.addEventListener('input', () => el.setCustomValidity(''));
  }

  function updateRequiredStates() {
    // Coordinates
    const coordSel = document.getElementById('coordinatesId');
    const needCoords = coordSel && coordSel.value === '';
    setRequired('coordX', !!needCoords);
    setRequired('coordY', !!needCoords);

    // Official address
    const offSel = document.getElementById('officialAddressId');
    const needOff = offSel && offSel.value === '';
    setRequired('officialStreet', !!needOff);

    // Postal address
    const sameEl = document.getElementById('samePostalEdit') || document.getElementById('samePostalCreate');
    const same = sameEl ? sameEl.checked : false;
    const postSel = document.getElementById('postalAddressId');
    const needPost = !same && postSel && postSel.value === '';
    setRequired('postalStreet', !!needPost);
  }

  function bindFill(selId, map) {
    const sel = document.getElementById(selId);
    if (!sel) return;
    function fill() {
      const opt = sel.options[sel.selectedIndex];
      if (!opt || !opt.value) return; // only when existing selected
      Object.entries(map).forEach(([k, attr]) => {
        const el = document.getElementById(k);
        if (el) el.value = opt.getAttribute(attr) ?? '';
      });
    }
    sel.addEventListener('change', fill);
    fill();
  }

  function updatePostalDisabled() {
    const cb = document.getElementById('samePostalEdit') || document.getElementById('samePostalCreate');
    const wrap = document.getElementById('postalFieldsEdit') || document.getElementById('postalFieldsCreate');
    if (!wrap) return;
    const disabled = !!(cb && cb.checked);
    wrap.querySelectorAll('input,select,textarea,button').forEach(el => { el.disabled = disabled; });
  }

  function init() {
    // Autofill from selected existing options
    bindFill('coordinatesId', { coordX: 'data-x', coordY: 'data-y' });
    bindFill('officialAddressId', { officialStreet: 'data-street', officialZipCode: 'data-zip' });
    bindFill('postalAddressId', { postalStreet: 'data-street', postalZipCode: 'data-zip' });

    // Disable postal fields if same as official
    updatePostalDisabled();
    const cb = document.getElementById('samePostalEdit') || document.getElementById('samePostalCreate');
    if (cb) cb.addEventListener('change', () => { updatePostalDisabled(); updateRequiredStates(); });
    // Attach validation messages
    attachValidity('coordX', 'Заполните X (или выберите координаты из списка)');
    attachValidity('coordY', 'Заполните Y (или выберите координаты из списка)');
    attachValidity('officialStreet', 'Укажите улицу (или выберите адрес из списка)');
    attachValidity('postalStreet', 'Укажите улицу (или выберите адрес из списка, либо отметьте совпадение)');

    // Toggle required states reactively
    ['coordinatesId', 'officialAddressId', 'postalAddressId', 'samePostalEdit', 'samePostalCreate']
      .forEach(id => { const el = document.getElementById(id); if (el) el.addEventListener('change', updateRequiredStates); });
    updateRequiredStates();

    // Subscribe to SSE updates and refresh table on changes from other users
    try {
      if (!!window.EventSource) {
        const es = new EventSource('/events/organizations');
        const closeSse = () => { try { es.close(); } catch (_) { } };
        window.addEventListener('beforeunload', closeSse);
        window.addEventListener('pagehide', closeSse);

        const handler = (ev) => {
          const hasTable = !!document.getElementById('org-table');
          if (!hasTable) return; // реагируем только на список
          try {
            const data = ev && ev.data ? JSON.parse(ev.data) : null;
            if (data && (data.type === 'created' || data.type === 'updated' || data.type === 'deleted')) {
              scheduleReload();
            } else {
              // На всякий случай перезагрузим и при неизвестном формате
              scheduleReload();
            }
          } catch (e) {
            scheduleReload();
          }
        };

        es.addEventListener('org', handler);
        es.onmessage = handler; // на случай, если имя события не проставлено
        // ping/ready можно игнорировать
      }
    } catch (e) {
      console.warn('SSE connection failed', e);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
