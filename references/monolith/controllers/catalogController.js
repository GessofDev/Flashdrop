const supabase = require('../config/supabase');

module.exports = {
  async listCategories(req, res) {
    const { data, error } = await supabase
      .from('categories')
      .select('id,name,description,image')
      .order('name');
    if (error) return res.status(500).json({ success: false, message: 'Error al listar categorias', error: error.message });
    return res.json({ success: true, data });
  },

  async listProducts(req, res) {
    const { data, error } = await supabase
      .from('products')
      .select('id,name,description,price,image,is_available,categories(id,name),restaurant(id,name,address)')
      .order('id', { ascending: false });
    if (error) return res.status(500).json({ success: false, message: 'Error al listar productos', error: error.message });

    const products = (data || []).map(product => ({
      id: product.id,
      name: product.name,
      description: product.description,
      price: Number(product.price),
      image: product.image,
      isAvailable: product.is_available,
      categoryId: product.categories?.id,
      categoryName: product.categories?.name,
      restaurantId: product.restaurant?.id,
      restaurantName: product.restaurant?.name,
      restaurantAddress: product.restaurant?.address
    }));
    return res.json({ success: true, data: products });
  },

  async createProduct(req, res) {
    const { category_id, restaurant_id, name, description, price, image, is_available } = req.body || {};
    if (!category_id || !restaurant_id || !name || !price) {
      return res.status(400).json({ success: false, message: 'Categoria, restaurante, nombre y precio son obligatorios' });
    }

    const { data, error } = await supabase
      .from('products')
      .insert({
        category_id: Number(category_id),
        restaurant_id: Number(restaurant_id),
        name,
        description: description || null,
        price: Number(price),
        image: image || null,
        is_available: is_available === undefined ? true : Boolean(Number(is_available))
      })
      .select('id')
      .single();
    if (error) return res.status(500).json({ success: false, message: 'Error al crear producto', error: error.message });
    return res.status(201).json({ success: true, message: 'Producto creado', data });
  },

  async listRestaurants(req, res) {
    const { data, error } = await supabase
      .from('restaurant')
      .select('id,name,address')
      .order('name');
    if (error) return res.status(500).json({ success: false, message: 'Error al listar restaurantes', error: error.message });
    return res.json({ success: true, data });
  }
};
