import React from 'react';
import { Clock, MapPin, Package, ArrowRight, User } from 'lucide-react';

interface PendingOrdersListProps {
  orders: any[];
  onSelectOrder: (order: any) => void;
  selectedOrderId: string | null;
}

export default function PendingOrdersList({ orders, onSelectOrder, selectedOrderId }: PendingOrdersListProps) {
  if (orders.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-8 text-center bg-slate-900 border-r border-slate-800 h-full">
        <Package className="w-12 h-12 text-slate-700 mb-4" />
        <h3 className="text-sm font-semibold text-slate-300">No Pending Orders</h3>
        <p className="text-xs text-slate-500 mt-2">All orders have been processed or dispatched.</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full bg-slate-900 border-r border-slate-800 w-[360px]">
      <div className="p-4 border-b border-slate-800 bg-slate-900/50">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-bold text-slate-100 flex items-center gap-2 uppercase tracking-wider">
            <Clock className="w-4 h-4 text-amber-500" />
            Pending Orders
          </h2>
          <span className="bg-amber-500/10 text-amber-400 text-[10px] font-bold px-2 py-0.5 rounded-full border border-amber-500/20">
            {orders.length}
          </span>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {orders.map((order) => (
          <button
            key={order.id}
            onClick={() => onSelectOrder(order)}
            className={`w-full text-left p-3 rounded-lg border transition-all duration-200 cursor-pointer ${
              selectedOrderId === order.id
                ? 'bg-emerald-900/20 border-emerald-500/50 shadow-[0_0_15px_rgba(16,185,129,0.1)]'
                : 'bg-slate-950/50 border-slate-800/50 hover:bg-slate-800/50 hover:border-slate-700'
            }`}
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-[10px] font-mono text-slate-400">#{order.id.slice(0, 8)}</span>
              <span className="text-[10px] text-slate-500 flex items-center gap-1">
                <Clock className="w-3 h-3" />
                {new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </span>
            </div>

            <div className="space-y-2">
              <div className="flex items-start gap-2">
                <MapPin className="w-3.5 h-3.5 text-blue-400 shrink-0 mt-0.5" />
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-slate-200 truncate">
                    {order.senderName || 'Sender'}
                  </p>
                  <p className="text-[10px] text-slate-500 truncate">
                    {order.senderPhone || 'No phone'}
                  </p>
                </div>
              </div>

              <div className="ml-1.5 pl-4 border-l border-slate-700/50 py-1 flex items-center">
                 <ArrowRight className="w-3 h-3 text-slate-600" />
              </div>

              <div className="flex items-start gap-2">
                <MapPin className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-0.5" />
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-slate-200 truncate">
                    {order.receiverName || 'Receiver'}
                  </p>
                  <p className="text-[10px] text-slate-500 truncate">
                    {order.receiverPhone || 'No phone'}
                  </p>
                </div>
              </div>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}
