(() => {
  async function reloadTable() {
    const container = document.getElementById('org-table');
    if (!container) return;
    const qs = window.location.search || '';
    try {
      const resp = await fetch(`/organizations/table${qs}`, {headers: {'X-Requested-With': 'fetch'}});
      if (resp.ok) {
        container.innerHTML = await resp.text();
      }
    } catch (e) {
      console.warn('Failed to reload table', e);
    }
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

  function bindFill(selId, map){
    const sel = document.getElementById(selId);
    if (!sel) return;
    function fill(){
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

  function updatePostalDisabled(){
    const cb = document.getElementById('samePostalEdit') || document.getElementById('samePostalCreate');
    const wrap = document.getElementById('postalFieldsEdit') || document.getElementById('postalFieldsCreate');
    if (!wrap) return;
    const disabled = !!(cb && cb.checked);
    wrap.querySelectorAll('input,select,textarea,button').forEach(el => { el.disabled = disabled; });
  }

  document.addEventListener('DOMContentLoaded', () => {
    // Autofill from selected existing options
    bindFill('coordinatesId', {coordX: 'data-x', coordY: 'data-y'});
    bindFill('officialAddressId', {officialStreet: 'data-street', officialZipCode: 'data-zip'});
    bindFill('postalAddressId', {postalStreet: 'data-street', postalZipCode: 'data-zip'});

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
    ['coordinatesId','officialAddressId','postalAddressId','samePostalEdit','samePostalCreate']
      .forEach(id => { const el = document.getElementById(id); if (el) el.addEventListener('change', updateRequiredStates); });
    updateRequiredStates();
  });
})();
