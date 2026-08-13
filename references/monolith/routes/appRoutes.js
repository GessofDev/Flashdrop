const catalogController = require('../controllers/catalogController');
const ordersController = require('../controllers/ordersController');

module.exports = (app) => {
  app.get('/api/categories', catalogController.listCategories);
  app.get('/api/restaurants', catalogController.listRestaurants);
  app.get('/api/products', catalogController.listProducts);
  app.post('/api/products', catalogController.createProduct);

  app.get('/api/orders', ordersController.listOrders);
  app.get('/api/orders/:id', ordersController.getOrderDetail);
  app.post('/api/orders', ordersController.createOrder);
  app.put('/api/orders/:id/status', ordersController.updateOrderStatus);
  app.post('/api/delivery/claim', ordersController.claimDeliveryOrders);
  app.get('/api/delivery/routes', ordersController.listDeliveryRoutes);
};
