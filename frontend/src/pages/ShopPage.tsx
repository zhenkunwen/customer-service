import { useEffect, useState } from 'react';

interface Product { id: number; name: string; pic: string; price: number; original_price: number; sub_title: string; sale: number; brand_name: string; }
interface CartItem { id: number; name: string; pic: string; price: number; qty: number; checked: boolean; }
interface UserInfo { id: number; username: string; phone: string; }

const api = (path: string, opts?: RequestInit) =>
  fetch(path, { ...opts, headers: { 'X-API-Key': 'change-me', 'Content-Type': 'application/json', ...opts?.headers } });

export default function ShopPage({ onGoChat }: { onGoChat: () => void }) {
  const [products, setProducts] = useState<Product[]>([]);
  const [user, setUser] = useState<UserInfo | null>(() => { try { return JSON.parse(localStorage.getItem('shop-user') || 'null'); } catch { return null; } });
  const [cart, setCart] = useState<CartItem[]>(() => { try { return JSON.parse(localStorage.getItem('shop-cart') || '[]'); } catch { return []; } });
  const [favorites, setFavorites] = useState<number[]>(() => { try { return JSON.parse(localStorage.getItem('shop-fav') || '[]'); } catch { return []; } });
  const [searchHistory, setSearchHistory] = useState<string[]>(() => { try { return JSON.parse(localStorage.getItem('shop-search') || '[]'); } catch { return []; } });
  const [page, setPage] = useState('home');
  const [searchQ, setSearchQ] = useState('');
  const [sortBy, setSortBy] = useState('sale');
  const [activeCat, setActiveCat] = useState('全部');
  const [showLogin, setShowLogin] = useState(false);
  const [isReg, setIsReg] = useState(false);
  const [loginName, setLoginName] = useState('');
  const [regPhone, setRegPhone] = useState('');
  const [detail, setDetail] = useState<Product | null>(null);
  const [showCheckout, setShowCheckout] = useState(false);
  const [address, setAddress] = useState('');
  const [receivePhone, setReceivePhone] = useState('');
  const [orders, setOrders] = useState<any[]>([]);
  const [showOrders, setShowOrders] = useState(false);

  useEffect(() => { api('/api/v1/shop/products').then(r => r.json()).then(setProducts).catch(() => {}); }, []);
  useEffect(() => { localStorage.setItem('shop-cart', JSON.stringify(cart)); }, [cart]);
  useEffect(() => { localStorage.setItem('shop-user', JSON.stringify(user)); }, [user]);
  useEffect(() => { localStorage.setItem('shop-fav', JSON.stringify(favorites)); }, [favorites]);
  useEffect(() => { localStorage.setItem('shop-search', JSON.stringify(searchHistory)); }, [searchHistory]);

  const loadProducts = (q?: string, sort?: string, cat?: string) => {
    const p = new URLSearchParams();
    if (q) p.set('q', q);
    if (sort && sort !== 'sale') p.set('sort', sort);
    if (cat && cat !== '全部') p.set('cat', cat);
    api('/api/v1/shop/products?' + p.toString()).then(r => r.json()).then(setProducts);
  };

  const toggleFav = (id: number) => setFavorites(f => f.includes(id) ? f.filter(x => x !== id) : [...f, id]);
  const addCart = (p: Product) => setCart(c => { const x = c.find(x => x.id === p.id); return x ? c.map(x => x.id === p.id ? { ...x, qty: x.qty + 1 } : x) : [...c, { id: p.id, name: p.name, pic: p.pic, price: p.price, qty: 1, checked: true }]; });
  const updateQty = (id: number, qty: number) => setCart(c => qty <= 0 ? c.filter(x => x.id !== id) : c.map(x => x.id === id ? { ...x, qty } : x));
  const toggleCheck = (id: number) => setCart(c => c.map(x => x.id === id ? { ...x, checked: !x.checked } : x));
  const checked = cart.filter(x => x.checked);
  const subtotal = checked.reduce((s, x) => s + x.price * x.qty, 0);
  const shipping = subtotal >= 199 ? 0 : 10;
  const cartTotal = subtotal + shipping;

  const login = async () => { const r = await api('/api/v1/shop/login', { method: 'POST', body: JSON.stringify({ username: loginName }) }).then(r => r.json()); if (r.error) return alert('用户不存在'); setUser(r); setShowLogin(false); };
  const register = async () => { if (!loginName.trim()) return alert('请输入用户名'); const r = await api('/api/v1/shop/register', { method: 'POST', body: JSON.stringify({ username: loginName, phone: regPhone }) }).then(r => r.json()); if (r.error) return alert(r.error); setUser(r); setShowLogin(false); };
  const search = () => { if (!searchQ.trim()) return; setSearchHistory(h => [searchQ, ...h.filter(x => x !== searchQ)].slice(0, 8)); loadProducts(searchQ, sortBy, activeCat === '全部' ? undefined : activeCat); };
  const submitOrder = async () => { if (!user || !address) return alert('请填收货地址'); const r = await api('/api/v1/shop/orders', { method: 'POST', body: JSON.stringify({ username: user.username, items: checked.map(x => ({ productId: x.id, name: x.name, pic: x.pic, price: x.price, qty: x.qty })), address, phone: receivePhone || user.phone }) }).then(r => r.json()); if (r.error) return alert(r.error); alert(`订单已提交！订单号: ${r.orderSn} 金额: ¥${r.total}`); setCart(c => c.filter(x => !x.checked)); setShowCheckout(false); };
  const loadOrders = async () => { if (!user) return; setOrders(await api('/api/v1/shop/myorders?user=' + user.username).then(r => r.json())); setShowOrders(true); };
  const categories = ['全部', '手机', '家电', '电脑', '数码', '服饰', '美妆', '食品'];
  const SORTS = [{ k: 'sale', v: '销量' }, { k: 'price_asc', v: '低价' }, { k: 'price_desc', v: '高价' }, { k: 'new', v: '最新' }];

  return <div style={{ fontFamily: '-apple-system, Roboto, sans-serif', background: '#f5f5f5', minHeight: '100vh', color: '#333' }}>
    {/* 顶栏 */}
    <div style={{ background: '#222', color: '#ccc', fontSize: 12, padding: '6px 0' }}>
      <div style={{ maxWidth: 1240, margin: '0 auto', padding: '0 20px', display: 'flex', justifyContent: 'space-between' }}>
        <span>易选Go</span>
        <span style={{ display: 'flex', gap: 16 }}>
          <span onClick={() => setPage('home')} style={{ cursor: 'pointer', color: page === 'home' ? '#ff6f00' : '#ccc' }}>首页</span>
          <span onClick={() => setPage('fav')} style={{ cursor: 'pointer', color: page === 'fav' ? '#ff6f00' : '#ccc' }}>❤️ {favorites.length}</span>
          <span onClick={() => setPage('cart')} style={{ cursor: 'pointer', color: page === 'cart' ? '#ff6f00' : '#ccc' }}>🛒 {cart.reduce((s, x) => s + x.qty, 0)}</span>
          {user ? <><span>👤 {user.username}</span><a href="#" onClick={e => { e.preventDefault(); loadOrders(); }} style={{ color: '#ccc' }}>订单</a></> : <a href="#" onClick={e => { e.preventDefault(); setShowLogin(true); }} style={{ color: '#ff6f00' }}>登录</a>}
          <span style={{ cursor: 'pointer', color: '#ff6f00' }} onClick={onGoChat}>客服</span>
        </span>
      </div>
    </div>

    {/* 主头 */}
    <div style={{ background: '#fff', padding: 16, boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
      <div style={{ maxWidth: 1240, margin: '0 auto', display: 'flex', gap: 20, alignItems: 'center' }}>
        <h1 style={{ fontSize: 28, fontWeight: 700, color: '#ff6f00', margin: 0 }}>易选Go</h1>
        <div style={{ flex: 1, display: 'flex', border: '2px solid #ff6f00', borderRadius: 24, overflow: 'hidden', maxWidth: 500 }}>
          <input value={searchQ} onChange={e => setSearchQ(e.target.value)} onKeyDown={e => e.key === 'Enter' && search()} placeholder="搜索商品" style={{ flex: 1, border: 'none', padding: '10px 16px', fontSize: 14, outline: 'none' }} />
          <button onClick={search} style={{ background: '#ff6f00', border: 'none', color: '#fff', padding: '0 24px', cursor: 'pointer' }}>搜索</button>
        </div>
        <button onClick={onGoChat} style={{ background: '#ff6f00', color: '#fff', border: 'none', borderRadius: 20, padding: '8px 20px', cursor: 'pointer', fontSize: 14 }}>客服</button>
      </div>
    </div>

    {/* 内容 */}
    <div style={{ maxWidth: 1240, margin: '0 auto', padding: '0 20px' }}>
      <div style={{ background: 'linear-gradient(135deg, #ff4400, #ff6f00)', color: '#fff', padding: '12px 0', margin: '16px 0', borderRadius: 20, textAlign: 'center', fontWeight: 700 }}>🔥 易选Go · 618好物节 限时特惠</div>

      {/* 分类栏 */}
      {page === 'home' && <div style={{ background: '#fff', borderRadius: 20, margin: '16px 0', padding: '12px 20px', display: 'flex', gap: 20, flexWrap: 'wrap' }}>
        {categories.map(c => <span key={c} onClick={() => { setActiveCat(c); loadProducts(searchQ, sortBy, c === '全部' ? undefined : c); }} style={{ cursor: 'pointer', fontSize: 14, color: activeCat === c ? '#ff6f00' : '#555', fontWeight: activeCat === c ? 600 : 400 }}>{c}</span>)}
      </div>}

      {/* 排序 */}
      {page === 'home' && <div style={{ display: 'flex', gap: 12, margin: '12px 0', alignItems: 'center', fontSize: 13 }}>
        <span style={{ color: '#999' }}>排序:</span>
        {SORTS.map(s => <span key={s.k} onClick={() => { setSortBy(s.k); loadProducts(searchQ, s.k, activeCat === '全部' ? undefined : activeCat); }} style={{ cursor: 'pointer', color: sortBy === s.k ? '#ff6f00' : '#555', fontWeight: sortBy === s.k ? 600 : 400 }}>{s.v}</span>)}
        {searchHistory.length > 0 && <span style={{ color: '#ccc', marginLeft: 'auto' }}>最近: {searchHistory.slice(0, 3).join(' · ')}</span>}
      </div>}

      {/* 首页 */}
      {page === 'home' && <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16, marginBottom: 30 }}>
        {products.map(p => <div key={p.id} style={{ background: '#fff', borderRadius: 16, overflow: 'hidden', cursor: 'pointer' }} onClick={() => setDetail(p)}>
          <div style={{ height: 160, background: '#f8f8f8' }}>{p.pic ? <img src={p.pic} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : null}</div>
          <div style={{ padding: 12 }}>
            <div style={{ fontSize: 14, fontWeight: 500, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.name}</div>
            <div><span style={{ color: '#ff3b00', fontSize: 18, fontWeight: 700 }}>¥{p.price}</span>{p.original_price > p.price && <span style={{ color: '#999', fontSize: 12, textDecoration: 'line-through', marginLeft: 6 }}>¥{p.original_price}</span>}</div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8 }}>
              <span style={{ fontSize: 20, cursor: 'pointer' }} onClick={e => { e.stopPropagation(); toggleFav(p.id); }}>{favorites.includes(p.id) ? '❤️' : '🤍'}</span>
              <button onClick={e => { e.stopPropagation(); addCart(p); }} style={{ background: '#ff6f00', color: '#fff', border: 'none', borderRadius: 16, padding: '4px 14px', cursor: 'pointer', fontSize: 12 }}>加购</button>
            </div>
          </div>
        </div>)}
      </div>}

      {/* 购物车页 */}
      {page === 'cart' && <div style={{ background: '#fff', borderRadius: 20, padding: 24, margin: '16px 0', minHeight: 300 }}>
        <h2 style={{ margin: '0 0 16px' }}>🛒 购物车</h2>
        {cart.length === 0 && <p style={{ color: '#ccc', textAlign: 'center', padding: 40 }}>购物车是空的</p>}
        {cart.map(x => { const valid = products.some(p => p.id === x.id); return <div key={x.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 0', borderBottom: '1px solid #f0f0f0', opacity: valid ? 1 : 0.5 }}>
          <input type="checkbox" checked={x.checked} onChange={() => toggleCheck(x.id)} disabled={!valid} />
          <img src={x.pic} alt="" style={{ width: 60, height: 60, borderRadius: 12, objectFit: 'cover', background: '#eee' }} />
          <div style={{ flex: 1 }}>{x.name}{!valid && <span style={{ color: '#ff3b00', fontSize: 11, marginLeft: 8 }}>已失效</span>}</div>
          <button onClick={() => updateQty(x.id, x.qty - 1)} style={{ border: '1px solid #ddd', borderRadius: 4, width: 28, cursor: 'pointer' }}>-</button>
          <span>{x.qty}</span>
          <button onClick={() => updateQty(x.id, x.qty + 1)} style={{ border: '1px solid #ddd', borderRadius: 4, width: 28, cursor: 'pointer' }}>+</button>
          <span style={{ color: '#ff3b00', fontWeight: 600, width: 90, textAlign: 'right' }}>¥{(x.price * x.qty).toFixed(2)}</span>
          <span onClick={() => updateQty(x.id, 0)} style={{ cursor: 'pointer', color: '#ccc', fontSize: 18 }}>×</span>
        </div>; })}
        {cart.length > 0 && <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 16, marginTop: 16, alignItems: 'center' }}>
          <span style={{ fontSize: 13, color: '#999' }}>{shipping === 0 ? '✅ 免运费' : `运费 ¥10（满199包邮）`}</span>
          <span style={{ fontSize: 22, fontWeight: 700, color: '#ff3b00' }}>¥{cartTotal.toFixed(2)}</span>
          {user ? <button onClick={() => setShowCheckout(true)} style={{ background: '#ff6f00', color: '#fff', border: 'none', borderRadius: 24, padding: '10px 36px', fontSize: 16, cursor: 'pointer' }}>去结算</button>
            : <button onClick={() => setShowLogin(true)} style={{ background: '#ccc', color: '#fff', border: 'none', borderRadius: 24, padding: '10px 36px', fontSize: 16, cursor: 'pointer' }}>请先登录</button>}
        </div>}
      </div>}

      {/* 收藏页 */}
      {page === 'fav' && <div style={{ background: '#fff', borderRadius: 20, padding: 24, margin: '16px 0', minHeight: 300 }}>
        <h2 style={{ margin: '0 0 16px' }}>❤️ 我的收藏</h2>
        {favorites.length === 0 && <p style={{ color: '#ccc', textAlign: 'center', padding: 40 }}>还没有收藏的商品</p>}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16 }}>
          {products.filter(p => favorites.includes(p.id)).map(p => <div key={p.id} style={{ background: '#f8f8f8', borderRadius: 16, padding: 12 }}>
            <img src={p.pic} alt="" style={{ width: '100%', height: 140, objectFit: 'cover', borderRadius: 12 }} />
            <div style={{ fontSize: 14, fontWeight: 500, marginTop: 8 }}>{p.name}</div>
            <div style={{ color: '#ff3b00', fontWeight: 700, margin: '4px 0' }}>¥{p.price}</div>
            <button onClick={() => addCart(p)} style={{ background: '#ff6f00', color: '#fff', border: 'none', borderRadius: 16, padding: '4px 14px', cursor: 'pointer', fontSize: 12 }}>加购</button>
          </div>)}
        </div>
      </div>}
    </div>

    {/* 登录弹窗 */}
    {showLogin && <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }} onClick={() => setShowLogin(false)}>
      <div style={{ background: '#fff', borderRadius: 24, padding: 32, width: 360 }} onClick={e => e.stopPropagation()}>
        <h2 style={{ margin: '0 0 20px' }}>{isReg ? '注册' : '登录'}</h2>
        <input value={loginName} onChange={e => setLoginName(e.target.value)} placeholder="用户名" style={{ width: '100%', padding: 12, marginBottom: 12, border: '1px solid #ddd', borderRadius: 12, fontSize: 14 }} />
        {isReg && <input value={regPhone} onChange={e => setRegPhone(e.target.value)} placeholder="手机号" style={{ width: '100%', padding: 12, marginBottom: 12, border: '1px solid #ddd', borderRadius: 12, fontSize: 14 }} />}
        {!isReg && <p style={{ fontSize: 12, color: '#999' }}>测试: windy / zhengsan / lisi</p>}
        <button onClick={isReg ? register : login} style={{ width: '100%', padding: 12, background: '#ff6f00', color: '#fff', border: 'none', borderRadius: 12, fontSize: 16, cursor: 'pointer' }}>{isReg ? '注册' : '登录'}</button>
        <p style={{ textAlign: 'center', marginTop: 12, fontSize: 13, cursor: 'pointer', color: '#ff6f00' }} onClick={() => setIsReg(!isReg)}>{isReg ? '已有账号？去登录' : '没有账号？去注册'}</p>
      </div>
    </div>}

    {/* 商品详情 */}
    {detail && <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }} onClick={() => setDetail(null)}>
      <div style={{ background: '#fff', borderRadius: 24, padding: 24, maxWidth: 500, width: '90%' }} onClick={e => e.stopPropagation()}>
        <img src={detail.pic} alt="" style={{ width: '100%', height: 250, objectFit: 'cover', borderRadius: 12, background: '#f0f0f0' }} />
        <h2 style={{ margin: '16px 0 8px' }}>{detail.name}</h2>
        <div><span style={{ color: '#ff3b00', fontSize: 28, fontWeight: 700 }}>¥{detail.price}</span>{detail.original_price > detail.price && <span style={{ color: '#999', textDecoration: 'line-through', marginLeft: 8 }}>¥{detail.original_price}</span>}</div>
        <div style={{ color: '#666', fontSize: 14, margin: '8px 0' }}>{detail.brand_name} · {detail.sub_title}</div>
        <button onClick={() => { addCart(detail); setDetail(null); }} style={{ width: '100%', padding: 14, background: '#ff6f00', color: '#fff', border: 'none', borderRadius: 12, fontSize: 16, cursor: 'pointer' }}>加入购物车</button>
      </div>
    </div>}

    {/* 结算弹窗 */}
    {showCheckout && <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }} onClick={() => setShowCheckout(false)}>
      <div style={{ background: '#fff', borderRadius: 24, padding: 24, maxWidth: 480, width: '90%' }} onClick={e => e.stopPropagation()}>
        <h2 style={{ margin: '0 0 16px' }}>确认订单</h2>
        {checked.map(x => <div key={x.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: 14, borderBottom: '1px solid #f0f0f0' }}><span>{x.name} x{x.qty}</span><span>¥{(x.price * x.qty).toFixed(2)}</span></div>)}
        <div style={{ textAlign: 'right', fontSize: 13, color: '#666', margin: '8px 0' }}>商品: ¥{subtotal.toFixed(2)} + 运费: ¥{shipping}</div>
        <div style={{ textAlign: 'right', fontSize: 18, fontWeight: 700 }}>应付: ¥{cartTotal.toFixed(2)}</div>
        <textarea value={address} onChange={e => setAddress(e.target.value)} placeholder="收货地址" rows={2} style={{ width: '100%', padding: 12, border: '1px solid #ddd', borderRadius: 12, fontSize: 14, margin: '12px 0 8px' }} />
        <input value={receivePhone} onChange={e => setReceivePhone(e.target.value)} placeholder="收货手机号" style={{ width: '100%', padding: 12, border: '1px solid #ddd', borderRadius: 12, fontSize: 14, marginBottom: 12 }} />
        <button onClick={submitOrder} style={{ width: '100%', padding: 14, background: '#ff6f00', color: '#fff', border: 'none', borderRadius: 12, fontSize: 16, cursor: 'pointer' }}>提交订单</button>
      </div>
    </div>}

    {/* 订单列表 */}
    {showOrders && <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }} onClick={() => setShowOrders(false)}>
      <div style={{ background: '#fff', borderRadius: 24, padding: 24, maxWidth: 600, width: '90%', maxHeight: '70vh', overflow: 'auto' }} onClick={e => e.stopPropagation()}>
        <h2 style={{ margin: '0 0 16px' }}>📋 我的订单</h2>
        {orders.length === 0 && <p style={{ color: '#999' }}>暂无订单</p>}
        {orders.map((o, i) => <div key={i} style={{ padding: '12px 0', borderBottom: '1px solid #f0f0f0' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}><span style={{ fontWeight: 500 }}>{o.order_sn}</span><span style={{ color: '#ff6f00' }}>{['待付款','待发货','已发货','已完成','已关闭'][o.status] || '未知'}</span></div>
          <div style={{ fontSize: 13, color: '#999' }}>¥{o.total_amount} · {String(o.create_time || '').substring(0, 10)}</div>
        </div>)}
      </div>
    </div>}

    <div style={{ background: '#fff', padding: 30, textAlign: 'center', fontSize: 12, color: '#888' }}>
      <p>© 2026 易选Go · 下单后找客服查订单/物流</p>
    </div>
  </div>;
}
