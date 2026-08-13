const supabase = require('../config/supabase');

async function firstId(table, column = 'id', filters = {}) {
  let request = supabase.from(table).select(column).order(column).limit(1);
  Object.entries(filters).forEach(([key, value]) => { request = request.eq(key, value); });
  const { data, error } = await request;
  if (error) throw error;
  return data?.[0]?.[column] || null;
}

module.exports = {
  async listOrders(req, res) {
    let restaurantId = null;
    const userId = Number(req.query?.user_id);
    if (Number.isInteger(userId) && userId > 0) {
      restaurantId = await firstId('restaurant', 'id', { user_id: userId });
      if (!restaurantId) return res.json({ success: true, data: [] });
    }

    let request = supabase
      .from('orders')
      .select('id,status,address,subtotal,delivery_fee,total,payment_method,created_at,client(users(name,last_name,email,phone)),restaurant(name),delivery(id)')
      .order('id', { ascending: false });
    if (restaurantId) request = request.eq('restaurant_id', restaurantId);

    const { data, error } = await request;
    if (error) return res.status(500).json({ success: false, message: 'Error al listar pedidos', error: error.message });

    const orders = (data || []).map(order => {
      const client = order.client?.users;
      return {
        id: order.id,
        code: `#FD-${`${order.id}`.padStart(4, '0')}`,
        status: order.status,
        address: order.address,
        subtotal: Number(order.subtotal),
        deliveryFee: Number(order.delivery_fee),
        total: Number(order.total),
        paymentMethod: order.payment_method,
        clientName: [client?.name, client?.last_name].filter(Boolean).join(' ') || 'Cliente',
        clientPhone: client?.phone || '',
        clientEmail: client?.email || '',
        restaurantName: order.restaurant?.name,
        deliveryId: order.delivery?.id,
        createdAt: order.created_at
      };
    });
    return res.json({ success: true, data: orders });
  },

  async createOrder(req, res) {
    try {
      const { user_id, product_id, quantity, items, address, payment_method, distance_km, estimated_minutes } = req.body || {};
      const requestedItems = Array.isArray(items) && items.length
        ? items.map(item => ({
          productId: Number(item.product_id ?? item.productId),
          quantity: Math.max(1, Number(item.quantity || 1))
        }))
        : [{ productId: Number(product_id), quantity: Math.max(1, Number(quantity || 1)) }];

      if (!address || requestedItems.some(item => !Number.isInteger(item.productId) || item.productId < 1)) {
        return res.status(400).json({ success: false, message: 'Producto y direccion son obligatorios' });
      }

      const productIds = [...new Set(requestedItems.map(item => item.productId))];
      const { data: products, error: productError } = await supabase
        .from('products')
        .select('id,restaurant_id,price,restaurant(name,address)')
        .in('id', productIds);
      if (productError) throw productError;
      if (!products || products.length !== productIds.length) {
        return res.status(404).json({ success: false, message: 'Uno o mas productos no existen' });
      }

      const restaurantId = products[0].restaurant_id;
      if (products.some(product => product.restaurant_id !== restaurantId)) {
        return res.status(400).json({ success: false, message: 'El pedido debe contener productos de un mismo local' });
      }

      const productById = new Map(products.map(product => [product.id, product]));
      const normalizedItems = requestedItems.map(item => {
        const product = productById.get(item.productId);
        const lineTotal = Number(product.price) * item.quantity;
        return { ...item, product, lineTotal };
      });

      let clientId = user_id ? await firstId('client', 'id', { user_id: Number(user_id) }) : null;
      if (!clientId) clientId = await firstId('client');
      if (!clientId) return res.status(400).json({ success: false, message: 'No existe cliente para crear pedido' });

      const subtotal = normalizedItems.reduce((sum, item) => sum + item.lineTotal, 0);
      const deliveryFee = 2500;
      const total = subtotal + deliveryFee;
      const distanceKm = Math.min(80, Math.max(0.5, Number(distance_km) || 3.2));
      const estimatedMinutes = Math.min(180, Math.max(10, Number(estimated_minutes) || 20));
      const { data: order, error: orderError } = await supabase
        .from('orders')
        .insert({
          client_id: clientId,
          restaurant_id: restaurantId,
          delivery_id: null,
          status: 'Nuevo pedido',
          address,
          subtotal,
          delivery_fee: deliveryFee,
          total,
          payment_method: payment_method || 'Efectivo'
        })
        .select('id')
        .single();
      if (orderError) throw orderError;

      const { error: itemError } = await supabase.from('order_items').insert(
        normalizedItems.map(item => ({
          order_id: order.id,
          product_id: item.productId,
          quantity: item.quantity,
          unit_price: item.product.price,
          total: item.lineTotal
        }))
      );
      if (itemError) throw itemError;

      const restaurant = products[0].restaurant;
      const { error: routeError } = await supabase.from('delivery_routes').insert({
        order_id: order.id,
        pickup_address: restaurant?.address || restaurant?.name || 'Restaurante',
        delivery_address: address,
        distance_km: Number(distanceKm.toFixed(2)),
        estimated_minutes: Math.round(estimatedMinutes),
        status: 'Pendiente'
      });
      if (routeError) throw routeError;

      return res.status(201).json({ success: true, message: 'Pedido creado', data: { id: order.id, total } });
    } catch (error) {
      return res.status(500).json({ success: false, message: 'Error al crear pedido', error: error.message });
    }
  },

  async getOrderDetail(req, res) {
    const orderId = Number(req.params.id);
    if (!Number.isInteger(orderId) || orderId < 1) {
      return res.status(400).json({ success: false, message: 'Pedido no valido' });
    }

    const { data: order, error } = await supabase
      .from('orders')
      .select(`
        id,status,address,subtotal,delivery_fee,total,payment_method,created_at,
        client(users(name,last_name,email,phone)),
        restaurant(name,address),
        delivery(vehicle,users(name,last_name,phone)),
        order_items(id,quantity,unit_price,total,products(name,description,image)),
        delivery_routes(id,pickup_address,delivery_address,distance_km,estimated_minutes,status)
      `)
      .eq('id', orderId)
      .maybeSingle();

    if (error) {
      return res.status(500).json({ success: false, message: 'Error al obtener el pedido', error: error.message });
    }
    if (!order) {
      return res.status(404).json({ success: false, message: 'Pedido no encontrado' });
    }

    const client = order.client?.users;
    const delivery = order.delivery?.users;
    const route = Array.isArray(order.delivery_routes)
      ? order.delivery_routes[0]
      : order.delivery_routes;

    return res.json({
      success: true,
      data: {
        id: order.id,
        code: `#FD-${`${order.id}`.padStart(4, '0')}`,
        status: order.status,
        address: order.address,
        subtotal: Number(order.subtotal),
        deliveryFee: Number(order.delivery_fee),
        total: Number(order.total),
        paymentMethod: order.payment_method,
        createdAt: order.created_at,
        client: {
          name: [client?.name, client?.last_name].filter(Boolean).join(' ') || 'Cliente',
          email: client?.email || '',
          phone: client?.phone || ''
        },
        restaurant: {
          name: order.restaurant?.name || 'Restaurante',
          address: order.restaurant?.address || ''
        },
        delivery: order.delivery ? {
          name: [delivery?.name, delivery?.last_name].filter(Boolean).join(' ') || 'Repartidor',
          phone: delivery?.phone || '',
          vehicle: order.delivery.vehicle || ''
        } : null,
        route: route ? {
          pickupAddress: route.pickup_address,
          deliveryAddress: route.delivery_address,
          distanceKm: Number(route.distance_km),
          estimatedMinutes: route.estimated_minutes,
          status: route.status
        } : null,
        items: (order.order_items || []).map(item => ({
          id: item.id,
          name: item.products?.name || 'Producto',
          description: item.products?.description || '',
          image: item.products?.image || '',
          quantity: item.quantity,
          unitPrice: Number(item.unit_price),
          total: Number(item.total)
        }))
      }
    });
  },

  async listDeliveryRoutes(req, res) {
    const { data, error } = await supabase
      .from('delivery_routes')
      .select(`
        id,order_id,pickup_address,delivery_address,distance_km,estimated_minutes,status,
        orders(
          id,status,total,payment_method,created_at,
          client(users(name,last_name,phone)),
          restaurant(name,address),
          delivery(id,user_id,users(name,last_name,phone)),
          order_items(quantity,products(name))
        )
      `)
      .order('distance_km', { ascending: true })
      .order('id', { ascending: false });
    if (error) return res.status(500).json({ success: false, message: 'Error al listar rutas', error: error.message });

    const routes = (data || []).map(route => {
      const order = route.orders;
      const user = order?.client?.users;
      const deliveryUser = order?.delivery?.users;
      const items = order?.order_items || [];
      return {
        id: route.id,
        orderId: route.order_id,
        code: `#FD-${`${route.order_id}`.padStart(4, '0')}`,
        pickupAddress: route.pickup_address,
        deliveryAddress: route.delivery_address,
        distanceKm: Number(route.distance_km),
        estimatedMinutes: route.estimated_minutes,
        status: order?.status || route.status,
        restaurantName: order?.restaurant?.name,
        deliveryId: order?.delivery?.id || null,
        deliveryUserId: order?.delivery?.user_id || null,
        deliveryName: [deliveryUser?.name, deliveryUser?.last_name].filter(Boolean).join(' ') || '',
        deliveryPhone: deliveryUser?.phone || '',
        clientName: [user?.name, user?.last_name].filter(Boolean).join(' ') || 'Cliente',
        clientPhone: user?.phone || '',
        total: Number(order?.total || 0),
        paymentMethod: order?.payment_method || '',
        itemCount: items.reduce((sum, item) => sum + Number(item.quantity || 0), 0),
        productNames: items.map(item => item.products?.name).filter(Boolean),
        createdAt: order?.created_at
      };
    });
    return res.json({ success: true, data: routes });
  },

  async claimDeliveryOrders(req, res) {
    try {
      const userId = Number(req.body?.user_id);
      const orderIds = [...new Set((req.body?.order_ids || [])
        .map(id => Number(id))
        .filter(id => Number.isInteger(id) && id > 0))];

      if (!Number.isInteger(userId) || userId < 1 || orderIds.length < 1 || orderIds.length > 3) {
        return res.status(400).json({
          success: false,
          message: 'Debes seleccionar entre 1 y 3 pedidos para tomar la ruta'
        });
      }

      const deliveryId = await firstId('delivery', 'id', { user_id: userId });
      if (!deliveryId) {
        return res.status(403).json({
          success: false,
          message: 'El usuario no tiene perfil de repartidor'
        });
      }

      const { data: active, error: activeError } = await supabase
        .from('orders')
        .select('id')
        .eq('delivery_id', deliveryId)
        .in('status', ['En camino', 'Retirado']);
      if (activeError) throw activeError;
      if ((active || []).length > 0) {
        return res.status(409).json({
          success: false,
          message: 'Ya tienes pedidos en ruta. Termina tu ruta antes de tomar mas pedidos'
        });
      }

      const { data: requested, error: requestError } = await supabase
        .from('orders')
        .select('id,status,restaurant_id,delivery_id')
        .in('id', orderIds);
      if (requestError) throw requestError;
      if ((requested || []).length !== orderIds.length) {
        return res.status(404).json({
          success: false,
          message: 'Uno o mas pedidos ya no estan disponibles'
        });
      }

      const blocked = requested.filter(order => ['En camino', 'Retirado', 'Entregado'].includes(order.status));
      if (blocked.length > 0) {
        return res.status(409).json({
          success: false,
          message: 'Uno o mas pedidos ya fueron tomados por otro repartidor'
        });
      }

      const restaurants = new Set(requested.map(order => order.restaurant_id));
      if (restaurants.size > 1) {
        return res.status(400).json({
          success: false,
          message: 'Solo puedes agrupar pedidos del mismo restaurante'
        });
      }

      const { data: claimed, error: claimError } = await supabase
        .from('orders')
        .update({ delivery_id: deliveryId, status: 'En camino' })
        .in('id', orderIds)
        .not('status', 'in', '("En camino","Retirado","Entregado")')
        .select('id');
      if (claimError) throw claimError;
      if ((claimed || []).length !== orderIds.length) {
        return res.status(409).json({
          success: false,
          message: 'Alguien tomo uno de estos pedidos antes que tu. Actualiza la lista'
        });
      }

      const { error: routeError } = await supabase
        .from('delivery_routes')
        .update({ status: 'En camino' })
        .in('order_id', orderIds);
      if (routeError) throw routeError;

      return res.json({
        success: true,
        message: 'Pedidos tomados. Tu ruta esta activa',
        data: { deliveryId, orderIds }
      });
    } catch (error) {
      return res.status(500).json({
        success: false,
        message: 'Error al tomar pedidos',
        error: error.message
      });
    }
  },

  async updateOrderStatus(req, res) {
    const orderId = Number(req.params.id);
    const status = `${req.body?.status || ''}`.trim();
    const allowedStatuses = ['Nuevo pedido', 'Preparando', 'Listo para retiro', 'Retirado', 'En camino', 'Entregado'];
    if (!Number.isInteger(orderId) || !allowedStatuses.includes(status)) {
      return res.status(400).json({ success: false, message: 'Estado no valido' });
    }

    const { error } = await supabase
      .from('orders')
      .update({ status })
      .eq('id', orderId);
    if (error) return res.status(500).json({ success: false, message: 'Error al actualizar estado', error: error.message });

    const { error: routeError } = await supabase
      .from('delivery_routes')
      .update({ status })
      .eq('order_id', orderId);
    if (routeError) return res.status(500).json({ success: false, message: 'Pedido actualizado, pero fallo la ruta', error: routeError.message });
    return res.json({ success: true, message: 'Estado actualizado' });
  }
};
