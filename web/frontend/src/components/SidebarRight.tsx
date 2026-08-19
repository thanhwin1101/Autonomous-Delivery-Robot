import { useState, FormEvent } from 'react';
import { Battery, User, MapPin, Check, AlertTriangle, Play, Lock, Unlock, ShoppingBag, Phone } from 'lucide-react';
import { AGV, Order } from '../types';

interface SidebarRightProps {
  selectedAgv: AGV | null;
  onForceStop: (id: string) => void;
  onUnlock: (id: string) => void;
  onAssignOrder: (id: string, order: Order) => void;
}

export default function SidebarRight({
  selectedAgv,
  onForceStop,
  onUnlock,
  onAssignOrder,
}: SidebarRightProps) {
  const [senderName, setSenderName] = useState('');
  const [receiverName, setReceiverName] = useState('');

  if (!selectedAgv) {
    return (
      <main className="fixed right-4 top-14 bottom-4 w-92 z-30 pointer-events-none">
        <div className="pointer-events-auto h-full bg-slate-900 border border-slate-800 rounded-md flex flex-col items-center justify-center p-6 text-center shadow-2xl">
          <div className="w-12 h-12 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-400 mb-4 animate-pulse">
            <MapPin className="w-5 h-5" />
          </div>
          <h3 className="text-sm font-bold text-slate-300 uppercase tracking-wider font-mono">No Vehicle Selected</h3>
          <p className="text-[11px] text-slate-400 mt-2 max-w-[240px]">
            Select an AGV marker on the map or use the Fleet Health directory to view live telemetry.
          </p>
        </div>
      </main>
    );
  }

  const { id, status, battery, type, lastPing, currentOrder, estimatedTimeRemaining, assignedStation } = selectedAgv;

  // Status style configuration
  const statusStyles = {
    DELIVERING: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    IDLE: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    CHARGING: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    STOPPED: 'bg-rose-500/15 text-rose-400 border-rose-500/30 animate-pulse',
    MAINTENANCE: 'bg-slate-500/20 text-slate-400 border-slate-500/20',
  };

  // Battery circle stroke offset calculation
  const radius = 24;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (battery / 100) * circumference;

  // Battery health text/color
  const batteryColor = battery > 70 ? 'text-emerald-400' : battery > 30 ? 'text-amber-400' : 'text-rose-400';
  const batteryCircleColor = battery > 70 ? '#10b981' : battery > 30 ? '#f59e0b' : '#f43f5e';

  // Handle Order dispatching from IDLE
  const handleDispatch = (e: FormEvent) => {
    e.preventDefault();
    if (!senderName || !receiverName) return;

    const newOrder: Order = {
      id: Math.floor(100 + Math.random() * 900).toString(),
      senderName,
      senderPhone: '+49 89 ' + Math.floor(100000 + Math.random() * 900000),
      receiverName,
      receiverPhone: '+49 89 ' + Math.floor(100000 + Math.random() * 900000),
      pickupTime: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      estimatedArrival: new Date(Date.now() + 20 * 60 * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      distanceRemaining: 680,
      progress: 'pickup',
    };

    onAssignOrder(selectedAgv.id, newOrder);
    setSenderName('');
    setReceiverName('');
  };

  return (
    <main className="fixed right-4 top-14 bottom-4 w-92 z-30 pointer-events-none">
      <div className="pointer-events-auto h-full bg-slate-900 border border-slate-800 rounded-md flex flex-col overflow-hidden shadow-2xl">
        
        {/* Sidebar Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between select-none">
          <div>
            <h2 className="text-sm font-bold text-slate-200 tracking-tight flex items-center gap-2 font-mono">
              {id} <span className="text-[10px] font-normal text-slate-500">({type})</span>
            </h2>
            <p className="text-[10px] text-slate-400 mt-1">Ping: {lastPing}s • Station: {assignedStation}</p>
          </div>
          <span className={`px-2 py-0.5 text-[9px] font-bold tracking-wider uppercase rounded border ${statusStyles[status]}`}>
            {status}
          </span>
        </div>

        {/* Scrollable Contents */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          
          {/* Battery Status widget */}
          <div className="flex items-center gap-4 bg-slate-950/40 border border-slate-800 p-3 rounded-md select-none">
            <div className="relative w-12 h-12 flex items-center justify-center">
              <svg className="w-full h-full transform -rotate-90">
                <circle
                  cx="24"
                  cy="24"
                  r={radius}
                  className="stroke-slate-800"
                  strokeWidth="3.5"
                  fill="transparent"
                />
                <circle
                  cx="24"
                  cy="24"
                  r={radius}
                  stroke={batteryCircleColor}
                  strokeWidth="3.5"
                  fill="transparent"
                  strokeDasharray={circumference}
                  strokeDashoffset={strokeDashoffset}
                  strokeLinecap="round"
                  className="transition-all duration-500"
                />
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center">
                <span className={`text-[11px] font-bold ${batteryColor}`}>{battery}%</span>
              </div>
            </div>
            <div>
              <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wide">Battery Telemetry</h4>
              <p className="text-[10px] text-slate-400 mt-0.5 font-mono">
                {status === 'CHARGING' ? 'FAST CHARGING' : `EST. ${estimatedTimeRemaining} LEFT`}
              </p>
            </div>
          </div>

          {/* Emergency Safety Alert (if Stopped) */}
          {status === 'STOPPED' && (
            <div className="p-3 bg-rose-500/10 border border-rose-500/20 rounded-md flex items-start gap-3">
              <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" />
              <div>
                <h4 className="text-xs font-bold text-rose-400 uppercase tracking-wider">Emergency Stop Engaged</h4>
                <p className="text-[10px] text-rose-300/80 leading-relaxed mt-1">
                  Obstacle detected or manual override triggered. Clearance required. Verify surroundings and unlock.
                </p>
              </div>
            </div>
          )}

          {/* Current Order details or Dispatch subform */}
          {status === 'DELIVERING' && currentOrder ? (
            <div className="space-y-2">
              <h4 className="text-[10px] font-bold text-slate-400 tracking-wider uppercase font-mono">Active Dispatch #{currentOrder.id}</h4>
              <div className="bg-slate-950/40 border border-slate-800 p-3 rounded-md space-y-3">
                <div className="flex items-start gap-3">
                  <div className="p-1 rounded bg-slate-800 text-slate-400 mt-0.5">
                    <User className="w-3 h-3" />
                  </div>
                  <div>
                    <p className="text-[9px] text-slate-500 uppercase font-mono">Sender / Pick-up</p>
                    <p className="text-xs font-bold text-slate-200 mt-0.5">{currentOrder.senderName}</p>
                    <p className="text-[10px] text-slate-400 mt-0.5 font-mono">{currentOrder.senderPhone}</p>
                  </div>
                </div>
                <div className="w-full h-px bg-slate-800" />
                <div className="flex items-start gap-3">
                  <div className="p-1 rounded bg-slate-800 text-slate-400 mt-0.5">
                    <MapPin className="w-3 h-3" />
                  </div>
                  <div>
                    <p className="text-[9px] text-slate-500 uppercase font-mono">Receiver / Destination</p>
                    <p className="text-xs font-bold text-slate-200 mt-0.5">{currentOrder.receiverName}</p>
                    <p className="text-[10px] text-slate-400 mt-0.5 font-mono">{currentOrder.receiverPhone}</p>
                  </div>
                </div>
              </div>
            </div>
          ) : status === 'IDLE' ? (
            <div className="space-y-2">
              <h4 className="text-[10px] font-bold text-amber-400 tracking-wider uppercase font-mono">Dispatch New Order</h4>
              <form onSubmit={handleDispatch} className="bg-slate-950/40 border border-slate-800 p-3 rounded-md space-y-3">
                <div>
                  <label className="block text-[9px] font-bold text-slate-500 uppercase tracking-wider font-mono">Sender Name</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Warehouse Sector A"
                    value={senderName}
                    onChange={(e) => setSenderName(e.target.value)}
                    className="w-full mt-1 px-2.5 py-1 text-xs bg-slate-900 border border-slate-800 rounded text-slate-200 focus:outline-none focus:border-slate-700 transition-colors placeholder-slate-600"
                  />
                </div>
                <div>
                  <label className="block text-[9px] font-bold text-slate-500 uppercase tracking-wider font-mono">Receiver Name</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Loading Dock 4"
                    value={receiverName}
                    onChange={(e) => setReceiverName(e.target.value)}
                    className="w-full mt-1 px-2.5 py-1 text-xs bg-slate-900 border border-slate-800 rounded text-slate-200 focus:outline-none focus:border-slate-700 transition-colors placeholder-slate-600"
                  />
                </div>
                <button
                  type="submit"
                  className="w-full py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded text-[10px] uppercase tracking-widest cursor-pointer transition-colors"
                >
                  Confirm Dispatch
                </button>
              </form>
            </div>
          ) : (
            <div className="bg-slate-950/40 border border-slate-800 p-4 rounded-md text-center select-none">
              <ShoppingBag className="w-4 h-4 text-slate-500 mx-auto mb-2" />
              <p className="text-xs text-slate-400 font-bold uppercase tracking-wider font-mono">No dispatch available</p>
              <p className="text-[10px] text-slate-500 mt-1">
                {status === 'CHARGING' ? 'Replenishing power source' : 'Active maintenance checklist outstanding.'}
              </p>
            </div>
          )}

          {/* Route Progress timeline (if Delivering) */}
          {status === 'DELIVERING' && currentOrder && (
            <div className="space-y-3">
              <h4 className="text-[10px] font-bold text-slate-400 tracking-wider uppercase font-mono">Route Tracking</h4>
              <div className="ml-2 border-l border-slate-800 pl-4 space-y-4 relative select-none">
                
                {/* Pick up Milestone */}
                <div className="relative">
                  <div className="absolute -left-[23px] top-0.5 w-2.5 h-2.5 rounded-full bg-emerald-500 flex items-center justify-center">
                    <Check className="w-1.5 h-1.5 text-slate-950 stroke-[3]" />
                  </div>
                  <p className="text-xs font-semibold text-slate-200">Pick up Completed</p>
                  <p className="text-[10px] text-slate-500 mt-0.5">Dock Bay • {currentOrder.pickupTime}</p>
                </div>

                {/* On the way Milestone */}
                <div className="relative">
                  <div className="absolute -left-[23px] top-0.5 w-2.5 h-2.5 rounded-full border border-emerald-500 bg-slate-950 flex items-center justify-center">
                    <span className="w-1 h-1 rounded-full bg-emerald-500 animate-ping" />
                  </div>
                  <p className="text-xs font-semibold text-emerald-400">In Transit</p>
                  <p className="text-[10px] text-slate-400 mt-0.5 font-mono">REM: {currentOrder.distanceRemaining}m</p>
                </div>

                {/* Delivered Milestone */}
                <div className="relative opacity-40">
                  <div className="absolute -left-[23px] top-0.5 w-2.5 h-2.5 rounded-full border border-slate-700 bg-slate-950" />
                  <p className="text-xs font-semibold text-slate-400">Arrival at Destination</p>
                  <p className="text-[10px] text-slate-500 mt-0.5 font-mono">EST: {currentOrder.estimatedArrival}</p>
                </div>

              </div>
            </div>
          )}

        </div>

        {/* Action Bottom Controls */}
        <div className="p-4 border-t border-slate-800 grid grid-cols-2 gap-3 bg-slate-950/40">
          <button
            onClick={() => onForceStop(id)}
            disabled={status === 'STOPPED' || status === 'MAINTENANCE'}
            className="flex items-center justify-center gap-1.5 py-2 bg-rose-600 hover:bg-rose-500 text-white font-bold rounded uppercase text-[10px] tracking-widest transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed select-none"
          >
            <Lock className="w-3 h-3" />
            <span>Emergency Stop</span>
          </button>
          <button
            onClick={() => onUnlock(id)}
            disabled={status !== 'STOPPED'}
            className="flex items-center justify-center gap-1.5 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded uppercase text-[10px] tracking-widest transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed select-none"
          >
            <Unlock className="w-3 h-3" />
            <span>Unlock Unit</span>
          </button>
        </div>

      </div>
    </main>
  );
}
