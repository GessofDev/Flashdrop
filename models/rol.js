const supabase = require('../config/supabase');

module.exports = {
  async create(id_user, id_rol) {
    const { data, error } = await supabase
      .from('user_has_roles')
      .insert({ id_user, id_rol })
      .select('id')
      .single();
    if (error) throw error;
    return data.id;
  }
};
