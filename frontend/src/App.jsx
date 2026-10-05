import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Activity, ArrowDownRight, ArrowLeft, ArrowRight, BadgeCheck, Banknote, Boxes,
  Check, CheckCircle2, ChevronDown, CircleAlert, Clock3, Cpu, CreditCard,
  Headphones, Heart, House, LayoutDashboard, LoaderCircle, LockKeyhole,
  Menu, PackageCheck, RefreshCw, RotateCcw, ShieldCheck, ShoppingBag,
  ShoppingCart, Smartphone, Sparkles, Timer, Truck, UserRound, Wallet,
  XCircle, Zap,
} from 'lucide-react';
import { api, newIdempotencyKey } from './api.js';

const REQUEST_TARGET = 10_000;
const SIMULATION_WORKERS = 32;
const PRODUCT_IMAGE = 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=1200&q=85';
const DELIVERY_STEPS = ['Order created', 'Payment confirmed', 'Processing', 'Shipped', 'Out for delivery', 'Delivered'];

function loadSaved(key) {
  try { return JSON.parse(localStorage.getItem(key) || 'null'); } catch { return null; }
}

function getCustomerId() {
  let id = localStorage.getItem('salestorm-customer-id');
  if (!id) {
    id = String(800000 + Math.floor(Math.random() * 190000));
    localStorage.setItem('salestorm-customer-id', id);
  }
  return Number(id);
}

function formatMoney(value, currency = 'USD') {
  const amount = Number(value || 0);
  return new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 2 }).format(amount);
}

function formatTime(seconds) {
  const safe = Math.max(0, seconds);
  return `${String(Math.floor(safe / 60)).padStart(2, '0')}:${String(safe % 60).padStart(2, '0')}`;
}

function StatusPill({ children, tone = 'neutral', dot = false }) {
  return <span className={`status-pill status-${tone}`}>{dot && <i aria-hidden="true" />}{children}</span>;
}

function StatCard({ label, value, detail, icon: Icon, tone = 'blue', emphasis = false }) {
  return (
    <article className={`stat-card stat-${tone}${emphasis ? ' stat-emphasis' : ''}`}>
      <div className="stat-heading"><span>{label}</span><Icon size={17} strokeWidth={1.8} /></div>
      <strong>{value}</strong>
      {detail && <small>{detail}</small>}
    </article>
  );
}

function InventoryBar({ inventory }) {
  if (!inventory) return <div className="inventory-bar-empty" aria-label="Inventory data unavailable" />;
  const total = Math.max(1, inventory.availableQuantity + inventory.reservedQuantity + inventory.soldQuantity);
  const available = inventory.availableQuantity / total * 100;
  const reserved = inventory.reservedQuantity / total * 100;
  const sold = inventory.soldQuantity / total * 100;
  return (
    <div className="inventory-bar" role="img" aria-label={`${inventory.availableQuantity} available, ${inventory.reservedQuantity} reserved, ${inventory.soldQuantity} sold`}>
      <span className="bar-available" style={{ width: `${available}%` }} />
      <span className="bar-reserved" style={{ width: `${reserved}%` }} />
      <span className="bar-sold" style={{ width: `${sold}%` }} />
    </div>
  );
}

function Countdown({ expiresAt, onExpire }) {
  const [seconds, setSeconds] = useState(() => Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000)));
  useEffect(() => {
    const tick = () => {
      const next = Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
      setSeconds(next);
      if (next === 0) onExpire?.();
    };
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [expiresAt, onExpire]);
  return <span className={seconds <= 30 ? 'countdown countdown-urgent' : 'countdown'}>{formatTime(seconds)}</span>;
}

function DemoDropCountdown() {
  const [seconds, setSeconds] = useState(24 * 60 + 16);
  useEffect(() => {
    const timer = window.setInterval(() => setSeconds((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return <span className="sale-clock">{formatTime(seconds)}</span>;
}

function ProductImage({ small = false }) {
  const [failed, setFailed] = useState(false);
  return (
    <div className={`product-image${small ? ' product-image-small' : ''}`}>
      {!failed ? <img src={PRODUCT_IMAGE} alt="Premium over-ear wireless headphones" onError={() => setFailed(true)} /> : (
        <div className="image-fallback"><Headphones size={small ? 34 : 70} strokeWidth={1.3} /><span>Studio Audio</span></div>
      )}
      <span className="image-caption">STUDIO SERIES · 01</span>
    </div>
  );
}

function Header({ page, setPage, cart, customerId, mobileOpen, setMobileOpen }) {
  const links = [
    { id: 'shop', label: 'Shop', icon: House },
    { id: 'cart', label: 'Cart', icon: ShoppingCart },
    { id: 'orders', label: 'Orders', icon: PackageCheck },
    { id: 'dashboard', label: 'System dashboard', icon: LayoutDashboard },
  ];
  return (
    <header className="site-header">
      <button className="brand" onClick={() => setPage('shop')} aria-label="SALESTORM home">
        <span className="brand-mark"><Zap size={19} fill="currentColor" /></span><span>SALESTORM</span>
      </button>
      <button className="mobile-menu" onClick={() => setMobileOpen(!mobileOpen)} aria-label="Toggle navigation"><Menu size={21} /></button>
      <nav className={`main-nav${mobileOpen ? ' nav-open' : ''}`} aria-label="Main navigation">
        {links.map(({ id, label }) => <button key={id} className={page === id ? 'nav-link nav-active' : 'nav-link'} onClick={() => { setPage(id); setMobileOpen(false); }}>{label}{id === 'cart' && cart ? <span className="cart-count">1</span> : null}</button>)}
      </nav>
      <div className="user-control"><span className="user-avatar"><UserRound size={16} /></span><span>Demo buyer</span><ChevronDown size={14} /><span className="user-id">ID {customerId}</span></div>
    </header>
  );
}

function App() {
  const [page, setPage] = useState('shop');
  const [products, setProducts] = useState([]);
  const [selectedProductId, setSelectedProductId] = useState(null);
  const [inventory, setInventory] = useState(null);
  const [reservation, setReservation] = useState(() => loadSaved('salestorm-reservation'));
  const [customerId] = useState(getCustomerId);
  const [loadingProducts, setLoadingProducts] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [paymentMode, setPaymentMode] = useState('SUCCESS');
  const [checkoutResult, setCheckoutResult] = useState(() => loadSaved('salestorm-payment'));
  const [sessionStats, setSessionStats] = useState({ paymentFailures: 0, paymentTimeouts: 0, duplicateRequests: 0 });
  const [simulation, setSimulation] = useState({ running: false, done: 0, succeeded: 0, rejected: 0, errors: 0, startAvailable: null, result: null });
  const [scenarioResult, setScenarioResult] = useState(null);
  const [health, setHealth] = useState(null);
  const [demoSummary, setDemoSummary] = useState(null);
  const [stockCapacity, setStockCapacity] = useState(null);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [workflowStage, setWorkflowStage] = useState('product');
  const reservationKeyRef = useRef(null);
  const lastReservationRequestRef = useRef(null);
  const paymentKeysRef = useRef({});
  const simulationAbortRef = useRef(null);
  const stockCapacityByProductRef = useRef({});

  const product = useMemo(() => products.find((item) => item.productId === selectedProductId) || products[0] || null, [products, selectedProductId]);
  const totalStock = inventory ? inventory.availableQuantity + inventory.reservedQuantity + inventory.soldQuantity : 0;
  const oversold = inventory && stockCapacity !== null
    ? Math.max(0, inventory.soldQuantity + inventory.reservedQuantity - stockCapacity)
    : null;
  const reservationActive = reservation && ['RESERVED', 'PAYMENT_PENDING'].includes(reservation.status);
  const lowStock = inventory && inventory.availableQuantity > 0 && inventory.availableQuantity <= Math.max(5, Math.ceil(totalStock * 0.15));

  const refreshInventory = useCallback(async (productId = selectedProductId) => {
    if (!productId) return;
    try {
      const response = await api.getDemoSummary(productId);
      const summary = response.inventory;
      setDemoSummary(response);
      setInventory(summary);
      if (stockCapacityByProductRef.current[productId] === undefined) {
        stockCapacityByProductRef.current[productId] = response.initialStock;
      }
      setStockCapacity(stockCapacityByProductRef.current[productId]);
    } catch (requestError) { setError(requestError.message); }
  }, [selectedProductId]);

  const refreshReservation = useCallback(async () => {
    if (!reservation?.reservationId) return;
    try {
      const updated = await api.getReservation(reservation.reservationId);
      setReservation(updated);
      localStorage.setItem('salestorm-reservation', JSON.stringify(updated));
      if (updated.status === 'EXPIRED') setNotice('Your reservation expired. The backend has released the inventory.');
    } catch (requestError) { setError(requestError.message); }
  }, [reservation?.reservationId]);

  useEffect(() => {
    let active = true;
    api.getProducts().then((items) => {
      if (!active) return;
      setProducts(items || []);
      const preferred = reservation?.productId && items?.some((item) => item.productId === reservation.productId)
        ? reservation.productId : items?.[0]?.productId;
      if (preferred) setSelectedProductId(preferred);
    }).catch((requestError) => active && setError(requestError.message)).finally(() => active && setLoadingProducts(false));
    api.health().then(setHealth).catch(() => setHealth({ status: 'UNAVAILABLE' }));
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!selectedProductId) return undefined;
    refreshInventory(selectedProductId);
    const poll = window.setInterval(() => refreshInventory(selectedProductId), 5000);
    return () => window.clearInterval(poll);
  }, [selectedProductId, refreshInventory]);

  useEffect(() => {
    if (!reservationActive) return undefined;
    const poll = window.setInterval(refreshReservation, 5000);
    return () => window.clearInterval(poll);
  }, [reservationActive, refreshReservation]);

  useEffect(() => () => simulationAbortRef.current?.abort(), []);

  const persistReservation = (value) => {
    setReservation(value);
    localStorage.setItem('salestorm-reservation', JSON.stringify(value));
  };

  const makeReservation = async ({ keepPage = true } = {}) => {
    if (!product) throw new Error('No flash-sale product is available.');
    const key = reservationKeyRef.current || newIdempotencyKey('reserve');
    reservationKeyRef.current = key;
    const body = { customerId, productId: product.productId, quantity: 1 };
    lastReservationRequestRef.current = { body, key };
    setBusy('reserve'); setError(''); setNotice(''); setWorkflowStage('reservation');
    try {
      const result = await api.createReservation(body, key);
      reservationKeyRef.current = null;
      persistReservation(result);
      setCheckoutResult(null);
      localStorage.removeItem('salestorm-payment');
      setNotice(result.duplicate ? 'This request was already processed. The existing reservation was returned.' : 'Inventory reserved for your checkout.');
      await refreshInventory(product.productId);
      if (keepPage) setPage('cart');
      return result;
    } finally { setBusy(''); }
  };

  const handleBuy = async () => {
    if (busy || !product) return;
    try { await makeReservation(); } catch (requestError) { setError(requestError.message); }
  };

  const handleDuplicateScenario = async () => {
    setBusy('scenario'); setError(''); setScenarioResult(null);
    try {
      const existing = lastReservationRequestRef.current;
      const original = existing || (() => null)();
      if (!original) await makeReservation({ keepPage: false });
      const request = lastReservationRequestRef.current;
      const response = await api.createReservation(request.body, request.key);
      setSessionStats((stats) => ({ ...stats, duplicateRequests: stats.duplicateRequests + (response.duplicate ? 1 : 0) }));
      setScenarioResult({ title: 'Duplicate request', result: response.duplicate ? 'Idempotent replay confirmed' : 'Reservation created', impact: 'No additional stock reserved', recovery: 'Existing reservation returned by API' });
      persistReservation(response);
      await refreshInventory(product.productId);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(''); }
  };

  const runPaymentScenario = async (mode, simulateOrderServiceFailure = false) => {
    setBusy(`payment-${mode}`); setError(''); setScenarioResult(null); setWorkflowStage('payment');
    try {
      let activeReservation = reservationActive ? reservation : null;
      if (!activeReservation) activeReservation = await makeReservation({ keepPage: false });
      const key = newIdempotencyKey(`scenario-${mode.toLowerCase()}`);
      const result = await api.checkout(activeReservation.reservationId, mode, key, simulateOrderServiceFailure);
      persistReservation({ ...activeReservation, status: result.reservationStatus });
      setCheckoutResult(result);
      localStorage.setItem('salestorm-payment', JSON.stringify(result));
      if (mode === 'FAILURE') setSessionStats((stats) => ({ ...stats, paymentFailures: stats.paymentFailures + 1 }));
      if (mode === 'TIMEOUT') setSessionStats((stats) => ({ ...stats, paymentTimeouts: stats.paymentTimeouts + 1 }));
      setScenarioResult({
        title: `Payment ${mode.toLowerCase()}`,
        result: `Gateway returned ${result.paymentStatus}`,
        impact: mode === 'FAILURE' ? 'Reservation released by backend' : mode === 'TIMEOUT' ? 'Inventory remains reserved while outcome is unknown' : 'Reservation confirmed',
        recovery: mode === 'TIMEOUT' ? 'Reservation expiry remains active' : result.orderStatus === 'PENDING_RECOVERY' ? 'Order saved for recovery without another charge' : 'State persisted by checkout API',
      });
      await refreshInventory(product.productId);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(''); }
  };

  const handleCheckout = async () => {
    if (!reservationActive || busy) return;
    setBusy('checkout'); setError(''); setNotice(''); setWorkflowStage('payment');
    try {
      const keyId = `${reservation.reservationId}:${paymentMode}`;
      const key = paymentKeysRef.current[keyId] || (paymentKeysRef.current[keyId] = newIdempotencyKey(`payment-${paymentMode.toLowerCase()}`));
      const result = await api.checkout(reservation.reservationId, paymentMode, key);
      setCheckoutResult(result);
      localStorage.setItem('salestorm-payment', JSON.stringify(result));
      persistReservation({ ...reservation, status: result.reservationStatus });
      if (result.paymentStatus === 'FAILURE') setSessionStats((stats) => ({ ...stats, paymentFailures: stats.paymentFailures + 1 }));
      if (result.paymentStatus === 'TIMEOUT') setSessionStats((stats) => ({ ...stats, paymentTimeouts: stats.paymentTimeouts + 1 }));
      setWorkflowStage(result.paymentStatus === 'SUCCESS' ? 'order' : 'payment');
      await refreshInventory(product.productId);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(''); }
  };

  const watchExpiry = async () => {
    if (!reservation?.reservationId) return;
    setBusy('expiry'); setError('');
    try {
      const current = await api.expireReservation(reservation.reservationId);
      persistReservation(current);
      setScenarioResult({ title: 'Reservation expiry', result: `Current status: ${current.status}`, impact: current.status === 'EXPIRED' ? 'Inventory released by backend' : 'Reservation was already terminal', recovery: 'Expiry is idempotent; stock is released once' });
      await refreshInventory(current.productId);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(''); }
  };

  const resetDemo = async () => {
    setBusy('reset'); setError(''); setNotice('');
    try {
      const result = await api.resetDemo();
      setDemoSummary(result);
      setInventory(result.inventory);
      setReservation(null);
      setCheckoutResult(null);
      setSimulation({ running: false, done: 0, succeeded: 0, rejected: 0, errors: 0, startAvailable: null, result: null });
      setSessionStats({ paymentFailures: 0, paymentTimeouts: 0, duplicateRequests: 0 });
      localStorage.removeItem('salestorm-reservation');
      localStorage.removeItem('salestorm-payment');
      setScenarioResult({ title: 'Demo reset', result: 'Demo records cleared', impact: `${result.inventory.availableQuantity} units available`, recovery: 'Inventory and demo state reset by backend' });
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(''); }
  };

  const runOrderFailureScenario = () => runPaymentScenario('SUCCESS', true);

  const recoverOrder = async () => {
    if (!checkoutResult?.orderId || busy) return;
    setBusy('recover-order'); setError('');
    try {
      const order = await api.recoverOrder(checkoutResult.orderId);
      const updated = { ...checkoutResult, orderStatus: order.status };
      setCheckoutResult(updated);
      localStorage.setItem('salestorm-payment', JSON.stringify(updated));
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(''); }
  };

  const runSimulation = async () => {
    if (simulation.running || !product) return;
    const startingAvailable = inventory?.availableQuantity ?? 0;
    const controller = new AbortController();
    simulationAbortRef.current = controller;
    let nextIndex = 0;
    let done = 0;
    let succeeded = 0;
    let rejected = 0;
    let errorsCount = 0;
    const runKey = newIdempotencyKey('load-run');
    setSimulation({ running: true, done: 0, succeeded: 0, rejected: 0, errors: 0, startAvailable: startingAvailable, result: null });
    setError(''); setNotice(''); setWorkflowStage('inventory');
    const workers = Array.from({ length: SIMULATION_WORKERS }, async () => {
      while (nextIndex < REQUEST_TARGET && !controller.signal.aborted) {
        const index = nextIndex++;
        try {
          await api.createReservation(
            { customerId: customerId + index + 1, productId: product.productId, quantity: 1 },
            `${runKey}-${index}`,
            controller.signal,
          );
          succeeded++;
        } catch (requestError) {
          if (requestError.status === 409 || requestError.status === 503) rejected++;
          else errorsCount++;
        }
        done++;
        if (done % 50 === 0 || done === REQUEST_TARGET || controller.signal.aborted) {
          setSimulation((current) => ({ ...current, done, succeeded, rejected, errors: errorsCount }));
        }
      }
    });
    await Promise.all(workers);
    simulationAbortRef.current = null;
    await refreshInventory(product.productId);
    setSimulation({
      running: false,
      done,
      succeeded,
      rejected,
      errors: errorsCount,
      startAvailable: startingAvailable,
      result: { requests: done, successfulReservations: succeeded, rejected, networkErrors: errorsCount, availableAtStart: startingAvailable, cancelled: controller.signal.aborted },
    });
    setWorkflowStage('reservation');
  };

  const stopSimulation = () => simulationAbortRef.current?.abort();

  const productPrice = Number(product?.price || 0);
  const displayProduct = product || { name: 'Flash sale product', description: 'A premium audio experience, built for everyday listening.' };

  return (
    <div className="app-shell">
      <Header page={page} setPage={setPage} cart={reservationActive} customerId={customerId} mobileOpen={mobileOpen} setMobileOpen={setMobileOpen} />
      {(error || notice) && <div className={`global-message ${error ? 'message-error' : 'message-success'}`} role={error ? 'alert' : 'status'}>
        {error ? <CircleAlert size={17} /> : <CheckCircle2 size={17} />}<span>{error || notice}</span>
        <button aria-label="Dismiss message" onClick={() => { setError(''); setNotice(''); }}><XCircle size={17} /></button>
      </div>}

      {page === 'shop' && <main className="store-page">
        <section className="store-hero" aria-labelledby="hero-title">
          <div className="hero-copy">
            <div className="hero-kicker"><span className="live-dot" /> LIVE DROP <span className="kicker-separator">/</span> LIMITED QUANTITY</div>
            <h1 id="hero-title">Flash sale.<br /><em>Built for scale.</em></h1>
            <p className="hero-subtitle">10,000 shoppers. 100 units. Zero overselling.</p>
            <div className="hero-product-line"><span className="product-index">DROP 001</span><span>{displayProduct.name}</span><span className="hero-price">{loadingProducts ? '—' : formatMoney(productPrice)}</span></div>
            <div className="hero-actions">
              <button className="button button-lime button-large" onClick={handleBuy} disabled={!inventory || inventory.availableQuantity === 0 || Boolean(busy) || loadingProducts}>
                {busy === 'reserve' ? <LoaderCircle className="spin" size={18} /> : <ShoppingBag size={18} />}
                {busy === 'reserve' ? 'Reserving inventory...' : inventory?.availableQuantity === 0 ? 'Sold out' : 'Reserve yours'}
                {!busy && inventory?.availableQuantity > 0 && <ArrowRight size={18} />}
              </button>
              <button className="button button-quiet-light" onClick={() => setPage('dashboard')}><Activity size={17} /> System dashboard</button>
            </div>
            <div className="hero-assurance"><ShieldCheck size={17} /><span>Inventory protected by concurrency-safe reservations</span></div>
          </div>
          <div className="hero-art-wrap">
            <div className="hero-art-glow" />
            <div className="hero-art-label"><span>01 / 01</span><span>ENGINEERED FOR IMMERSION</span></div>
            <ProductImage />
            <div className="hero-sticker"><Zap size={15} fill="currentColor" /><span>FLASH<br />DROP</span></div>
          </div>
          <div className="hero-footer"><span>SALESTORM SELECTS</span><span>RELIABLE BY DESIGN</span><span>01 — 03 OCT</span></div>
        </section>

        <section className="store-body">
          <div className="section-intro"><div><p className="eyebrow">THE DROP</p><h2>One moment. One great find.</h2></div><span className="section-meta"><span className="live-dot" /> INVENTORY UPDATES LIVE</span></div>
          {loadingProducts ? <LoadingPanel label="Loading the drop" /> : products.length === 0 ? <EmptyPanel title="No products are available" detail="The product service returned an empty catalog. Check back shortly." /> : (
            <div className="product-detail-layout">
              <div className="product-card-featured">
                <ProductImage />
                <button className="favorite-button" aria-label="Add product to favorites"><Heart size={18} /></button>
                <span className="product-card-tag"><Zap size={13} fill="currentColor" /> FLASH SALE</span>
              </div>
              <div className="product-info">
                <div className="product-meta"><span>SALESTORM SELECTS</span><span>SKU {String(product?.productId || '—').padStart(4, '0')}</span></div>
                <h3>{displayProduct.name}</h3>
                <p className="product-description">{displayProduct.description || 'Premium sound, considered design, and all-day comfort. A limited-run essential for your everyday soundtrack.'}</p>
                <div className="price-row"><strong>{formatMoney(productPrice)}</strong><span className="price-caption">FLASH PRICE</span></div>
                <div className="sale-clock-row"><span><Timer size={17} /> DEMO DROP WINDOW</span><DemoDropCountdown /></div>
                <div className="stock-title"><span>LIVE INVENTORY</span><span>{inventory ? `${inventory.availableQuantity} available` : 'Connecting...'}</span></div>
                <InventoryBar inventory={inventory} />
                <div className="inventory-legend">
                  <span><i className="legend-dot dot-blue" />Available <b>{inventory?.availableQuantity ?? '—'}</b></span>
                  <span><i className="legend-dot dot-amber" />Reserved <b>{inventory?.reservedQuantity ?? '—'}</b></span>
                  <span><i className="legend-dot dot-green" />Sold <b>{inventory?.soldQuantity ?? '—'}</b></span>
                </div>
                {lowStock && <p className="stock-warning"><ArrowDownRight size={15} /> Moving quickly. {inventory.availableQuantity} remaining.</p>}
                <button className="button button-navy button-buy-wide" onClick={handleBuy} disabled={!inventory || inventory.availableQuantity === 0 || Boolean(busy)}>
                  {busy === 'reserve' ? <LoaderCircle className="spin" size={18} /> : <LockKeyhole size={17} />}
                  {inventory?.availableQuantity === 0 ? 'SOLD OUT' : busy === 'reserve' ? 'Reserving inventory...' : 'Reserve & continue'}
                  {!busy && inventory?.availableQuantity > 0 && <ArrowRight size={18} />}
                </button>
                <p className="button-note">Your unit is held temporarily while you complete checkout.</p>
              </div>
            </div>
          )}
          <div className="trust-strip"><div><ShieldCheck size={20} /><span><b>Inventory integrity</b><small>Versioned stock updates</small></span></div><div><RefreshCw size={19} /><span><b>Idempotent requests</b><small>Safe retries, same result</small></span></div><div><Clock3 size={19} /><span><b>Temporary holds</b><small>Unpaid stock returns</small></span></div></div>
        </section>
      </main>}

      {page === 'cart' && <main className="content-page cart-page">
        <PageHeading eyebrow="YOUR BAG" title="Ready when you are." subtitle="A short reservation window keeps the drop fair for everyone." onBack={() => setPage('shop')} />
        {!reservation ? <EmptyPanel title="Your cart is waiting" detail="Reserve a unit from the live drop to begin checkout."><button className="button button-navy" onClick={() => setPage('shop')}>Explore the drop <ArrowRight size={16} /></button></EmptyPanel> : (
          <div className="cart-layout">
            <section className="cart-items panel">
              <div className="panel-heading"><span>ITEMS IN YOUR BAG</span><StatusPill tone={reservation.status === 'RESERVED' || reservation.status === 'PAYMENT_PENDING' ? 'blue' : reservation.status === 'CONFIRMED' ? 'green' : 'amber'} dot>{reservation.status.replace('_', ' ')}</StatusPill></div>
              <div className="cart-item"><ProductImage small /><div className="cart-item-info"><span className="eyebrow">LIMITED DROP</span><h3>{products.find((item) => item.productId === reservation.productId)?.name || displayProduct.name}</h3><p>Quantity <b>{reservation.quantity}</b></p><p>Reservation #{reservation.reservationId}</p></div><strong>{formatMoney((products.find((item) => item.productId === reservation.productId)?.price || productPrice) * reservation.quantity)}</strong></div>
              {reservation.expiresAt && reservationActive && <div className="reservation-callout"><div className="reservation-icon"><Timer size={19} /></div><div><b>Inventory reserved</b><small>Complete checkout before your hold expires.</small></div><div className="reservation-count"><Countdown expiresAt={reservation.expiresAt} onExpire={refreshReservation} /><small>TIME LEFT</small></div></div>}
              {reservation.status === 'EXPIRED' && <div className="notice-expired"><CircleAlert size={19} /><span><b>Your reservation expired.</b> Inventory has been released by the backend.</span><button className="button button-small" onClick={() => setPage('shop')}>Try again</button></div>}
              {reservation.status === 'PAYMENT_FAILED' && <div className="notice-expired"><CircleAlert size={19} /><span><b>Payment failed.</b> Your inventory reservation has been released.</span><button className="button button-small" onClick={() => setPage('shop')}>Shop again</button></div>}
              {reservation.status === 'CONFIRMED' && <div className="notice-confirmed"><CheckCircle2 size={19} /><span><b>Payment confirmed.</b> Order {checkoutResult?.orderId ? `#${checkoutResult.orderId}` : 'is being confirmed'}.</span></div>}
            </section>
            <aside className="order-summary panel"><div className="panel-heading"><span>ORDER SUMMARY</span><LockKeyhole size={16} /></div><div className="summary-line"><span>Subtotal</span><b>{formatMoney((products.find((item) => item.productId === reservation.productId)?.price || productPrice) * reservation.quantity)}</b></div><div className="summary-line"><span>Shipping</span><span>Calculated after order service integration</span></div><div className="summary-total"><span>Estimated total</span><strong>{formatMoney((products.find((item) => item.productId === reservation.productId)?.price || productPrice) * reservation.quantity)}</strong></div>
              {reservationActive && <button className="button button-navy button-full" onClick={() => setPage('checkout')}>Proceed to checkout <ArrowRight size={17} /></button>}
              <p className="secure-note"><ShieldCheck size={15} /> Secure checkout · Mock payment only</p>
            </aside>
          </div>
        )}
        <PurchaseSteps active={reservation?.status === 'CONFIRMED' ? 4 : reservationActive ? 2 : 1} />
      </main>}

      {page === 'checkout' && <main className="content-page checkout-page">
        <PageHeading eyebrow="SECURE CHECKOUT" title="Almost yours." subtitle="Your reserved inventory is held while you complete this demo checkout." onBack={() => setPage('cart')} />
        <PurchaseSteps active={3} />
        {!reservationActive ? <EmptyPanel title="No active reservation" detail="Reserve a unit first. Expired or failed reservations cannot be checked out."><button className="button button-navy" onClick={() => setPage('shop')}>Return to the drop</button></EmptyPanel> : (
          <div className="checkout-layout">
            <section className="checkout-form panel">
              <div className="checkout-section-title"><span className="step-number">01</span><div><h3>Customer information</h3><p>Demo profile linked to this reservation</p></div></div>
              <div className="customer-fields"><label>Customer ID<input readOnly value={customerId} /></label><label>Delivery email<input type="email" placeholder="demo@salestorm.test" /></label></div>
              <div className="checkout-section-title payment-title"><span className="step-number">02</span><div><h3>Payment method</h3><p>Choose a presentation method for the simulation</p></div></div>
              <div className="method-options" role="group" aria-label="Payment method">
                {[['Card', CreditCard], ['UPI', Smartphone], ['Wallet', Wallet]].map(([method, Icon]) => <button key={method} className="method-option" aria-pressed={method === 'Card'} onClick={(event) => { document.querySelectorAll('.method-option').forEach((button) => button.setAttribute('aria-pressed', 'false')); event.currentTarget.setAttribute('aria-pressed', 'true'); }}><Icon size={19} /><span>{method}</span>{method === 'Card' && <Check size={15} />}</button>)}
              </div>
              <div className="simulation-box"><div className="simulation-title"><span className="simulation-symbol"><Cpu size={18} /></span><div><b>Payment simulation</b><small>This prototype uses a mock payment gateway for demonstration.</small></div><StatusPill tone="amber">DEMO</StatusPill></div><div className="payment-outcomes">
                {['SUCCESS', 'FAILURE', 'TIMEOUT'].map((mode) => <button key={mode} className={`outcome-button${paymentMode === mode ? ' outcome-selected' : ''} outcome-${mode.toLowerCase()}`} onClick={() => setPaymentMode(mode)} aria-pressed={paymentMode === mode}><span className="outcome-check">{paymentMode === mode ? <Check size={13} /> : null}</span>{mode}</button>)}
              </div></div>
              {checkoutResult?.paymentStatus === 'SUCCESS' && <div className="checkout-success"><span className="success-mark"><CheckCircle2 size={22} /></span><div><b>Payment successful</b><small>{checkoutResult.orderStatus === 'PENDING_RECOVERY' ? 'Order is safely queued for recovery.' : `Order #${checkoutResult.orderId} confirmed.`}</small></div><Sparkles size={18} /></div>}
              {checkoutResult?.paymentStatus === 'FAILURE' && <div className="checkout-failure"><CircleAlert size={19} /><span><b>Payment failed.</b> Your inventory reservation has been released.</span></div>}
              {checkoutResult?.paymentStatus === 'TIMEOUT' && <div className="checkout-timeout"><Timer size={19} /><span><b>Payment timed out.</b> Inventory remains reserved while the outcome is unknown; reservation expiry will release it if needed.</span></div>}
              <button className="button button-navy button-full pay-button" onClick={handleCheckout} disabled={!reservationActive || Boolean(busy)}>{busy === 'checkout' ? <><LoaderCircle className="spin" size={18} /> Processing payment...</> : <><LockKeyhole size={17} /> Pay {formatMoney((products.find((item) => item.productId === reservation.productId)?.price || productPrice) * reservation.quantity)} <ArrowRight size={17} /></>}</button>
            </section>
            <aside className="checkout-aside"><div className="panel summary-product"><div className="panel-heading"><span>YOUR ITEM</span><span>QTY 01</span></div><div className="summary-product-row"><ProductImage small /><div><b>{products.find((item) => item.productId === reservation.productId)?.name || displayProduct.name}</b><span>{formatMoney((products.find((item) => item.productId === reservation.productId)?.price || productPrice) * reservation.quantity)}</span></div></div><div className="summary-line"><span>Reservation</span><StatusPill tone="blue" dot>{reservation.status.replace('_', ' ')}</StatusPill></div><div className="summary-line"><span>Expires in</span>{reservation.expiresAt ? <Countdown expiresAt={reservation.expiresAt} onExpire={refreshReservation} /> : <span>—</span>}</div><div className="summary-total"><span>Order total</span><strong>{formatMoney((products.find((item) => item.productId === reservation.productId)?.price || productPrice) * reservation.quantity)}</strong></div></div><div className="reassurance-card"><ShieldCheck size={19} /><div><b>Inventory reserved</b><p>Concurrency-safe stock holds ensure one unit is never promised twice.</p></div></div></aside>
          </div>
        )}
      </main>}

      {page === 'orders' && <main className="content-page orders-page"><PageHeading eyebrow="ORDER HISTORY" title="Your order." subtitle="Order and payment status returned by SALESTORM." onBack={() => setPage('shop')} />
        {checkoutResult?.paymentStatus === 'SUCCESS' ? <section className="order-confirm-panel panel"><div className="order-confirm-icon"><CheckCircle2 size={30} /></div><div><StatusPill tone={checkoutResult.orderStatus === 'CONFIRMED' ? 'green' : 'amber'} dot>{checkoutResult.orderStatus === 'CONFIRMED' ? 'ORDER CONFIRMED' : 'RECOVERY PENDING'}</StatusPill><h2>{checkoutResult.orderStatus === 'CONFIRMED' ? 'Order confirmed.' : 'Payment secured.'}</h2><p>Order #{checkoutResult.orderId} · Payment #{checkoutResult.paymentId} · Reservation #{checkoutResult.reservationId}</p>{checkoutResult.orderStatus === 'PENDING_RECOVERY' && <div className="order-limits"><strong>Order creation is queued for recovery</strong><span>Payment and inventory remain confirmed. Recovery does not charge again.</span><button className="button button-navy" onClick={recoverOrder} disabled={Boolean(busy)}>{busy === 'recover-order' ? <LoaderCircle className="spin" size={15} /> : <RefreshCw size={15} />} Recover order</button></div>}</div></section> : <EmptyPanel title="No completed payments yet" detail="Complete a successful checkout to see the confirmed order here." />}
        <div className="timeline-panel panel"><div className="panel-heading"><span>FULFILLMENT TIMELINE</span><StatusPill>NOT CONNECTED</StatusPill></div><div className="timeline-list">{DELIVERY_STEPS.map((step, index) => <div key={step} className={`timeline-step${checkoutResult?.paymentStatus === 'SUCCESS' && index < 2 ? ' timeline-done' : ''}`}><span className="timeline-node">{checkoutResult?.paymentStatus === 'SUCCESS' && index < 2 ? <Check size={13} /> : index + 1}</span><span>{step}</span><span className="timeline-state">{checkoutResult?.paymentStatus === 'SUCCESS' && index === 1 ? 'CONFIRMED' : 'AWAITING ORDER SERVICE'}</span></div>)}</div></div>
      </main>}

      {page === 'dashboard' && <main className="dashboard-page">
        <div className="dashboard-head"><div><p className="eyebrow">SALESTORM / OPERATIONS</p><h1>System control center</h1><p>High-concurrency flash sale monitoring. Stock and payment state are sourced from the active backend.</p></div><div className="dashboard-live"><span className={`health-indicator ${health?.status === 'UP' ? 'health-up' : 'health-down'}`} /><span>API {health?.status === 'UP' ? 'HEALTHY' : health?.status === 'UNAVAILABLE' ? 'UNREACHABLE' : 'CHECKING'}</span><button className="icon-button" aria-label="Refresh system data" onClick={() => { refreshInventory(); api.health().then(setHealth).catch(() => setHealth({ status: 'UNAVAILABLE' })); }}><RefreshCw size={16} /></button></div></div>
        <div className="dashboard-note"><CircleAlert size={17} /><span><b>Live demo environment.</b> Simulation sends real requests to the reservation API and can consume available inventory. Use Reset Demo to clear persisted attempts and restore the configured initial stock.</span></div>
        <section className="kpi-grid" aria-label="System key performance indicators">
          <StatCard label="Total requests" value={simulation.result?.requests ?? simulation.done} detail="Actual responses this run" icon={Activity} tone="navy" />
          <StatCard label="Successful reservations" value={demoSummary?.successfulReservations ?? '—'} detail="Persisted reservations from API" icon={CheckCircle2} tone="green" />
          <StatCard label="Available stock" value={inventory?.availableQuantity ?? '—'} detail="Live from inventory API" icon={Boxes} tone="blue" />
          <StatCard label="Reserved stock" value={inventory?.reservedQuantity ?? '—'} detail="Live from inventory API" icon={LockKeyhole} tone="amber" />
          <StatCard label="Sold stock" value={inventory?.soldQuantity ?? '—'} detail="Live from inventory API" icon={ShoppingBag} tone="green" />
          <StatCard label="Failed payments" value={demoSummary?.failedPayments ?? '—'} detail="Persisted gateway outcomes" icon={XCircle} tone="red" />
          <StatCard label="Released reservations" value={demoSummary?.releasedReservations ?? '—'} detail="Failure, expiry, or release" icon={ArrowDownRight} tone="amber" />
          <StatCard label="Overselling" value={demoSummary?.overselling ?? '—'} detail={demoSummary?.overselling === 0 ? 'Protection active · none detected' : demoSummary ? 'Inventory invariant exceeded' : 'Waiting for API'} icon={ShieldCheck} tone={oversold === 0 ? 'green' : 'red'} emphasis />
        </section>

        <div className="dashboard-columns">
          <section className="simulation-panel panel">
            <div className="panel-heading"><div><p className="eyebrow">LOAD EXERCISE</p><h2>10,000 concurrent purchase requests</h2></div><StatusPill tone={simulation.running ? 'amber' : 'green'} dot>{simulation.running ? 'RUNNING' : 'READY'}</StatusPill></div>
            <p className="simulation-intro">A live reservation API exercise with 32 concurrent workers. Every success and rejection is counted from its actual backend response.</p>
            <div className="simulation-metrics"><div><span>REQUEST TARGET</span><b>{REQUEST_TARGET.toLocaleString()}</b></div><div><span>MAXIMUM INVENTORY</span><b>{demoSummary?.initialStock ?? '—'}</b></div><div><span>ACTUAL RESERVATIONS</span><b>{simulation.succeeded}</b></div><div><span>OVERSOLD</span><b className={oversold === 0 ? 'text-green' : 'text-red'}>{demoSummary?.overselling ?? '—'}</b></div></div>
            {simulation.running && <div className="run-progress"><div className="progress-label"><span>Requests completed</span><b>{simulation.done.toLocaleString()} / {REQUEST_TARGET.toLocaleString()}</b></div><div className="progress-track"><span style={{ width: `${simulation.done / REQUEST_TARGET * 100}%` }} /></div><small>{simulation.succeeded} reserved · {simulation.rejected} rejected · {simulation.errors} network/API errors</small></div>}
            {simulation.result && <div className="simulation-result"><div className="result-icon"><CheckCircle2 size={19} /></div><div><b>{simulation.result.cancelled ? 'Simulation stopped' : `${simulation.result.requests.toLocaleString()} requests processed`}</b><p>{simulation.result.successfulReservations} reservations successful · {simulation.result.rejected} rejected · {simulation.result.networkErrors} errors</p><small>Inventory consistency: {oversold === 0 ? 'PASSED' : oversold === null ? 'UNVERIFIED' : 'CHECK REQUIRED'} · based on live inventory values</small></div></div>}
            <div className="simulation-actions"><button className="button button-navy" onClick={simulation.running ? stopSimulation : runSimulation} disabled={!simulation.running && (!product || !inventory)}>{simulation.running ? <><XCircle size={17} /> Stop simulation</> : <><Activity size={17} /> Run 10,000-request simulation</>}</button><small>Creates actual temporary reservations; stock returns after expiry if unpaid.</small></div>
          </section>

          <section className="inventory-panel panel"><div className="panel-heading"><div><p className="eyebrow">INVENTORY CONTROL</p><h2>Stock integrity</h2></div><StatusPill tone={oversold === 0 ? 'green' : 'neutral'} dot>{oversold === 0 ? 'HEALTHY' : 'CHECKING'}</StatusPill></div>
            <div className="inventory-total"><span>TOTAL TRACKED UNITS</span><b>{inventory ? totalStock : '—'}</b></div><InventoryBar inventory={inventory} /><div className="inventory-table"><div><i className="legend-dot dot-blue" /><span>AVAILABLE</span><b>{inventory?.availableQuantity ?? '—'}</b></div><div><i className="legend-dot dot-amber" /><span>RESERVED</span><b>{inventory?.reservedQuantity ?? '—'}</b></div><div><i className="legend-dot dot-green" /><span>SOLD</span><b>{inventory?.soldQuantity ?? '—'}</b></div></div>
            <div className="integrity-checks"><p><CheckCircle2 size={16} /><span>Inventory consistency</span><b>{inventory ? 'HEALTHY' : 'PENDING'}</b></p><p><CheckCircle2 size={16} /><span>Overselling protection</span><b>{inventory ? 'ACTIVE' : 'PENDING'}</b></p><p><Activity size={16} /><span>Reservation reconciliation</span><b>TTL SCHEDULER</b></p></div>
          </section>
        </div>

        <div className="dashboard-columns reliability-columns">
          <section className="scenarios-panel panel"><div className="panel-heading"><div><p className="eyebrow">FAULT INJECTION</p><h2>Failure & reliability scenarios</h2></div><StatusPill tone="blue">API-BACKED</StatusPill></div><p className="panel-description">Available flows call the reservation and checkout APIs directly. Results below reflect backend responses.</p>
            <div className="scenario-list">
              <ScenarioButton icon={RotateCcw} title="Simulate duplicate request" detail="Replay the last reservation idempotency key" action="Run scenario" busy={busy === 'scenario'} onClick={handleDuplicateScenario} />
              <ScenarioButton icon={XCircle} title="Simulate payment failure" detail="Failure releases the reservation in the backend" action="Run scenario" busy={busy === 'payment-FAILURE'} onClick={() => runPaymentScenario('FAILURE')} />
              <ScenarioButton icon={Timer} title="Simulate payment timeout" detail="Timeout keeps stock reserved while outcome is unknown" action="Run scenario" busy={busy === 'payment-TIMEOUT'} onClick={() => runPaymentScenario('TIMEOUT')} />
              <ScenarioButton icon={Clock3} title="Simulate reservation expiry" detail={reservation ? 'Force-expire and release this reservation' : 'Reserve an item before running this scenario'} action="Expire now" busy={busy === 'expiry'} disabled={!reservation || Boolean(busy)} onClick={watchExpiry} />
              <ScenarioButton icon={CircleAlert} title="Simulate order service failure" detail="Payment succeeds; order is saved for recovery" action="Run scenario" busy={busy === 'payment-SUCCESS'} disabled={Boolean(busy)} onClick={runOrderFailureScenario} />
              <ScenarioButton icon={RotateCcw} title="Reset demo" detail="Clear persisted demo records and restore initial stock" action="Reset" busy={busy === 'reset'} disabled={Boolean(busy) || simulation.running} onClick={resetDemo} />
            </div>
            {scenarioResult && <div className="scenario-result"><b>{scenarioResult.title}</b><dl><dt>Result</dt><dd>{scenarioResult.result}</dd><dt>Inventory impact</dt><dd>{scenarioResult.impact}</dd><dt>Recovery</dt><dd>{scenarioResult.recovery}</dd></dl></div>}
          </section>

          <section className="health-panel panel"><div className="panel-heading"><div><p className="eyebrow">OBSERVABILITY</p><h2>System health</h2></div><StatusPill tone={health?.status === 'UP' ? 'green' : 'amber'} dot>{health?.status === 'UP' ? 'API UP' : health?.status === 'UNAVAILABLE' ? 'API UNAVAILABLE' : 'CHECKING'}</StatusPill></div>
            <div className="health-explainer"><Activity size={16} /><span>Only the application-level health endpoint is real. Individual service states below are architecture placeholders, not health probes.</span></div>
            <div className="service-list">{[['API / Spring Boot', health?.status === 'UP' ? 'Healthy' : health?.status === 'UNAVAILABLE' ? 'Unreachable' : 'Checking', true], ['Product service', 'Not instrumented', false], ['Inventory service', 'Not instrumented', false], ['Reservation service', 'Not instrumented', false], ['Checkout & payment', 'Not instrumented', false], ['Order / fulfillment', 'Not connected', false]].map(([label, status, live]) => <div className="service-row" key={label}><span className={`service-indicator ${live && health?.status === 'UP' ? 'service-green' : live ? 'service-red' : 'service-demo'}`} /><span>{label}</span><small>{status}</small></div>)}</div>
            <div className="observability-note"><b>Session observations</b><div><span>Duplicate replays</span><strong>{sessionStats.duplicateRequests}</strong></div><div><span>Payment failures</span><strong>{sessionStats.paymentFailures}</strong></div><div><span>Payment timeouts</span><strong>{sessionStats.paymentTimeouts}</strong></div><div><span>Reservation success rate</span><strong>{simulation.done ? `${Math.round(simulation.succeeded / simulation.done * 100)}%` : 'No run yet'}</strong></div></div>
          </section>
        </div>
        <section className="workflow-panel panel"><div className="panel-heading"><div><p className="eyebrow">PURCHASE LIFECYCLE</p><h2>From first click to front door</h2></div><span className="workflow-caption">DEMO ARCHITECTURE</span></div><div className="workflow-track">{[['Customer', UserRound], ['Product', ShoppingBag], ['Inventory check', Boxes], ['Reservation', LockKeyhole], ['Checkout', CreditCard], ['Payment', Banknote], ['Order', PackageCheck], ['Fulfillment', Cpu], ['Shipment', Truck], ['Delivery', BadgeCheck]].map(([label, Icon], index) => <div className={`workflow-node${workflowStage === ['customer','product','inventory','reservation','checkout','payment','order'][index] ? ' workflow-current' : ''}${index <= ['customer','product','inventory','reservation','checkout','payment','order'].indexOf(workflowStage) ? ' workflow-done' : ''}`} key={label}><span><Icon size={17} /></span><small>{label}</small>{index < 9 && <i aria-hidden="true" />}</div>)}</div><p className="workflow-footnote">Currently active stages are inferred from this browser's API actions. Order, fulfillment, shipment, and delivery events are not provided by the backend.</p></section>
      </main>}

      <footer className="site-footer"><div className="footer-brand"><span className="brand-mark"><Zap size={15} fill="currentColor" /></span><b>SALESTORM</b></div><span>Concurrency-safe commerce, demonstrated live.</span><span>API {import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}</span></footer>
    </div>
  );
}

function PageHeading({ eyebrow, title, subtitle, onBack }) {
  return <div className="page-heading"><button className="back-link" onClick={onBack}><ArrowLeft size={16} /> Back</button><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{subtitle}</p></div>;
}

function PurchaseSteps({ active }) {
  return <div className="purchase-steps">{['Cart', 'Reservation', 'Checkout', 'Payment', 'Order'].map((label, index) => <div className={`purchase-step${index + 1 <= active ? ' purchase-step-active' : ''}`} key={label}><span>{index + 1 < active ? <Check size={13} /> : index + 1}</span><b>{label}</b>{index < 4 && <i />}</div>)}</div>;
}

function LoadingPanel({ label }) {
  return <div className="empty-panel"><LoaderCircle className="spin" size={26} /><b>{label}</b><span>Connecting to the SALESTORM API.</span></div>;
}

function EmptyPanel({ title, detail, children }) {
  return <div className="empty-panel"><span className="empty-icon"><ShoppingBag size={23} /></span><b>{title}</b><span>{detail}</span>{children}</div>;
}

function ScenarioButton({ icon: Icon, title, detail, action, busy, disabled, onClick }) {
  return <div className={`scenario-row${disabled ? ' scenario-disabled' : ''}`}><span className="scenario-icon"><Icon size={17} /></span><div className="scenario-copy"><b>{title}</b><small>{detail}</small></div><button className="scenario-action" onClick={onClick} disabled={disabled || busy}>{busy ? <LoaderCircle className="spin" size={15} /> : action}</button></div>;
}

export default App;