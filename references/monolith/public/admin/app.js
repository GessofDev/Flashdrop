const $ = (selector) => document.querySelector(selector);

async function api(path, options) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  return res.json();
}

function money(value) {
  return new Intl.NumberFormat('es-CL', {
    style: 'currency',
    currency: 'CLP',
    maximumFractionDigits: 0,
  }).format(Number(value || 0));
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function dateTime(value) {
  if (!value) return 'Sin fecha';
  return new Intl.DateTimeFormat('es-CL', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

async function loadSelects() {
  const [categories, restaurants] = await Promise.all([
    api('/api/categories'),
    api('/api/restaurants'),
  ]);

  $('#categorySelect').innerHTML = categories.data
    .map((item) => `<option value="${item.id}">${item.name}</option>`)
    .join('');

  $('#restaurantSelect').innerHTML = restaurants.data
    .map((item) => `<option value="${item.id}">${item.name}</option>`)
    .join('');
}

async function loadProducts() {
  const result = await api('/api/products');
  $('#productCount').textContent = result.data.length;
  $('#productList').innerHTML = result.data
    .map((product) => `
      <div class="item">
        <div class="thumb">${product.name.substring(0, 2).toUpperCase()}</div>
        <div>
          <strong>${product.name}</strong>
          <small>${product.restaurantName} - ${product.categoryName}</small>
          <span>${money(product.price)}</span>
        </div>
      </div>
    `)
    .join('');
}

async function loadOrders() {
  const result = await api('/api/orders');
  $('#orderCount').textContent = result.data.length;
  $('#ordersTable').innerHTML = result.data
    .map((order) => `
      <tr>
        <td><strong>${order.code}</strong></td>
        <td>${order.clientName}</td>
        <td>${order.restaurantName}</td>
        <td class="address-cell">${escapeHtml(order.address)}</td>
        <td><span class="pill">${order.status}</span></td>
        <td>${money(order.total)}</td>
        <td><button class="secondary-button" type="button" data-order-id="${order.id}">Ver detalle</button></td>
      </tr>
    `)
    .join('');
}

async function openOrderDetail(orderId) {
  const dialog = $('#orderDialog');
  $('#orderDialogTitle').textContent = 'Cargando...';
  $('#orderDetail').innerHTML = '<p class="empty-state">Consultando el pedido...</p>';
  dialog.showModal();

  try {
    const result = await api(`/api/orders/${orderId}`);
    if (!result.success) throw new Error(result.message || 'No se pudo cargar el pedido');
    const order = result.data;
    const route = order.route;

    $('#orderDialogTitle').textContent = `${order.code} - ${order.status}`;
    $('#orderDetail').innerHTML = `
      <section class="detail-summary">
        <div><small>Fecha</small><strong>${dateTime(order.createdAt)}</strong></div>
        <div><small>Pago</small><strong>${escapeHtml(order.paymentMethod)}</strong></div>
        <div><small>Total</small><strong>${money(order.total)}</strong></div>
      </section>

      <section class="detail-grid">
        <div class="detail-block">
          <h3>Cliente</h3>
          <strong>${escapeHtml(order.client.name)}</strong>
          <span>${escapeHtml(order.client.phone || 'Sin telefono')}</span>
          <span>${escapeHtml(order.client.email || 'Sin correo')}</span>
        </div>
        <div class="detail-block destination-block">
          <h3>Destino de entrega</h3>
          <strong>${escapeHtml(order.address)}</strong>
          ${route ? `<span>${route.distanceKm} km - ${route.estimatedMinutes} min estimados</span>` : '<span>Ruta aun no asignada</span>'}
        </div>
        <div class="detail-block">
          <h3>Retiro</h3>
          <strong>${escapeHtml(order.restaurant.name)}</strong>
          <span>${escapeHtml(route?.pickupAddress || order.restaurant.address || 'Sin direccion')}</span>
        </div>
        <div class="detail-block">
          <h3>Repartidor</h3>
          ${order.delivery ? `
            <strong>${escapeHtml(order.delivery.name)}</strong>
            <span>${escapeHtml(order.delivery.vehicle || 'Vehiculo no informado')}</span>
            <span>${escapeHtml(order.delivery.phone || 'Sin telefono')}</span>
          ` : '<strong>Sin asignar</strong><span>Disponible para tomar el pedido</span>'}
        </div>
      </section>

      <section class="detail-items">
        <h3>Productos</h3>
        ${order.items.length ? order.items.map((item) => `
          <div class="detail-item">
            <div>
              <strong>${item.quantity} x ${escapeHtml(item.name)}</strong>
              <small>${escapeHtml(item.description)}</small>
            </div>
            <span>${money(item.total)}</span>
          </div>
        `).join('') : '<p class="empty-state">Este pedido no tiene productos registrados.</p>'}
      </section>

      <section class="totals">
        <div><span>Subtotal</span><strong>${money(order.subtotal)}</strong></div>
        <div><span>Envio</span><strong>${money(order.deliveryFee)}</strong></div>
        <div class="total-row"><span>Total</span><strong>${money(order.total)}</strong></div>
      </section>
    `;
  } catch (error) {
    $('#orderDialogTitle').textContent = 'No se pudo abrir el pedido';
    $('#orderDetail').innerHTML = `<p class="error-state">${escapeHtml(error.message)}</p>`;
  }
}

async function loadRoutes() {
  const result = await api('/api/delivery/routes');
  $('#routeCount').textContent = result.data.length;
  $('#routesList').innerHTML = result.data
    .map((route) => `
      <div class="item">
        <div class="thumb">RT</div>
        <div>
          <strong>${route.code} - ${route.status}</strong>
          <small>Retiro: ${route.pickupAddress}</small>
          <small>Entrega: ${route.deliveryAddress}</small>
          <span>${route.distanceKm} km - ${route.estimatedMinutes} min</span>
        </div>
      </div>
    `)
    .join('');
}

async function refresh() {
  await Promise.all([loadProducts(), loadOrders(), loadRoutes()]);
}

$('#productForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.target).entries());
  await api('/api/products', {
    method: 'POST',
    body: JSON.stringify(data),
  });
  event.target.reset();
  await refresh();
});

$('#refreshBtn').addEventListener('click', refresh);

$('#ordersTable').addEventListener('click', (event) => {
  const button = event.target.closest('[data-order-id]');
  if (button) openOrderDetail(button.dataset.orderId);
});

$('#closeOrderDialog').addEventListener('click', () => $('#orderDialog').close());

$('#orderDialog').addEventListener('click', (event) => {
  if (event.target === event.currentTarget) event.currentTarget.close();
});

loadSelects().then(refresh).catch((error) => {
  console.error(error);
  alert('No se pudo conectar con el backend. Intenta actualizar nuevamente.');
});
