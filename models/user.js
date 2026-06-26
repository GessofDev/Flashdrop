const supabase = require('../config/supabase');
const bcrypt = require('bcryptjs');

async function findUser(filters) {
  let request = supabase
    .from('users')
    .select('id,email,name,rut,last_name,phone,photo');

  if (filters.id) request = request.eq('id', filters.id);
  if (filters.email) request = request.eq('email', filters.email);

  const { data: user, error } = await request.maybeSingle();
  if (error) throw error;
  if (!user) return null;

  const [{ data: login, error: loginError }, { data: roleLinks, error: rolesError }] = await Promise.all([
    supabase.from('login').select('password').eq('id_users', user.id).maybeSingle(),
    supabase.from('user_has_roles').select('roles(id,name,image,route)').eq('id_user', user.id)
  ]);

  if (loginError) throw loginError;
  if (rolesError) throw rolesError;

  return {
    id: user.id,
    email: user.email,
    name: user.name,
    rut: user.rut,
    lastName: user.last_name,
    phone: user.phone,
    photo: user.photo,
    passwordHash: login?.password || null,
    roles: (roleLinks || []).map(link => link.roles).filter(Boolean)
  };
}

const User = {
  findById(id) {
    return findUser({ id });
  },

  findByEmail(email) {
    return findUser({ email });
  },

  async findRolesByUserId(userId) {
    const { data, error } = await supabase
      .from('user_has_roles')
      .select('roles(name)')
      .eq('id_user', userId);
    if (error) throw error;
    return (data || []).map(item => item.roles?.name).filter(Boolean);
  },

  async create(user) {
    const hash = await bcrypt.hash(user.password, 10);
    const { data: created, error: userError } = await supabase
      .from('users')
      .insert({
        email: user.email,
        rut: user.rut || null,
        name: user.name || null,
        last_name: user.lastName || user.last_name || null,
        phone: user.phone || null,
        photo: user.photo || null
      })
      .select('id')
      .single();
    if (userError) throw userError;

    const userId = created.id;
    const { error: loginError } = await supabase.from('login').insert({
      login: user.email,
      password: hash,
      id_users: userId,
      status: 1
    });
    if (loginError) {
      await supabase.from('users').delete().eq('id', userId);
      throw loginError;
    }

    const { error: clientError } = await supabase.from('client').insert({ user_id: userId });
    if (clientError) throw clientError;
    return userId;
  }
};

module.exports = User;
