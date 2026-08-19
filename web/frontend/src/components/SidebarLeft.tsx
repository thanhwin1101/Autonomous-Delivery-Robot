import { useState } from 'react';
import { Cpu, LayoutDashboard, Compass, Wrench, History, Sliders, HelpCircle, LogOut, ChevronLeft, ChevronRight, TestTube, Clock, Search, ChevronDown, ChevronUp, MapPin, ArrowRight } from 'lucide-react';

interface SidebarLeftProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
  activeRobotsCount: number;
  onDeployClick: () => void;
  isCollapsed: boolean;
  setIsCollapsed: (val: boolean) => void;
  pendingOrders?: any[];
  selectedPendingOrder?: any | null;
  onSelectPendingOrder?: (order: any) => void;
}

export default function SidebarLeft({
  activeTab,
  setActiveTab,
  activeRobotsCount,
  onDeployClick,
  isCollapsed,
  setIsCollapsed,
  pendingOrders = [],
  selectedPendingOrder,
  onSelectPendingOrder,
}: SidebarLeftProps) {
  const [isPendingExpanded, setIsPendingExpanded] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const menuItems = [
    { id: 'dashboard', label: 'LIVE TRACKING', icon: Compass },
    { id: 'testing', label: 'TEST LAB', icon: TestTube },
  ];

  const filteredOrders = pendingOrders.filter(order => {
    const q = searchQuery.toLowerCase();
    const sName = (order.senderName || '').toLowerCase();
    const rName = (order.receiverName || '').toLowerCase();
    const sPhone = (order.senderPhone || '').toLowerCase();
    const rPhone = (order.receiverPhone || '').toLowerCase();
    return sName.includes(q) || rName.includes(q) || sPhone.includes(q) || rPhone.includes(q);
  });

  return (
    <aside className={`fixed left-0 top-0 h-full flex flex-col z-40 bg-slate-900 border-r border-slate-800 shadow-2xl pt-20 transition-all duration-300 ${isCollapsed ? 'w-20' : 'w-80'}`}>
      
      {/* Collapse Toggle Button */}
      <button 
        onClick={() => setIsCollapsed(!isCollapsed)}
        className="absolute -right-3 top-20 w-6 h-6 bg-slate-800 border border-slate-700 rounded-full flex items-center justify-center text-slate-400 hover:text-white hover:bg-slate-700 cursor-pointer z-50 transition-colors"
      >
        {isCollapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
      </button>

      {/* Fleet Selector/Summary Card */}
      <div className={`px-4 mb-4 transition-opacity duration-200 ${isCollapsed ? 'opacity-0 hidden' : 'opacity-100 block'}`}>
        <div className="flex items-center gap-3 p-3 bg-slate-800/40 border border-slate-800 rounded-md select-none">
          <div className="w-8 h-8 rounded-md bg-emerald-500/10 flex items-center justify-center text-emerald-400 shrink-0">
            <Cpu className="w-4 h-4" />
          </div>
          <div className="truncate">
            <h3 className="text-xs font-bold text-slate-300 leading-none uppercase tracking-wide truncate">Fleet Alpha</h3>
            <p className="text-[10px] font-mono text-emerald-400 mt-1 truncate">{activeRobotsCount} ACTIVE UNITS</p>
          </div>
        </div>
      </div>
      
      {/* Collapsed Logo (Shown only when collapsed) */}
      <div className={`flex justify-center mb-4 transition-opacity duration-200 ${isCollapsed ? 'opacity-100 block' : 'opacity-0 hidden'}`}>
         <div className="w-10 h-10 rounded-md bg-emerald-500/10 flex items-center justify-center text-emerald-400">
            <Cpu className="w-5 h-5" />
          </div>
      </div>

      {/* Main navigation links */}
      <nav className={`flex-1 space-y-0.5 ${isCollapsed ? 'px-2' : 'px-2'} overflow-y-auto overflow-x-hidden`}>
        {menuItems.map((item) => {
          const Icon = item.icon;
          const isSelected = activeTab === item.id;

          return (
            <button
              key={item.id}
              onClick={() => setActiveTab(item.id)}
              title={isCollapsed ? item.label : undefined}
              className={`w-full flex items-center gap-3 py-2.5 rounded-md select-none cursor-pointer transition-all duration-150 group text-left ${
                isCollapsed ? 'justify-center px-0' : 'px-4'
              } ${
                isSelected
                  ? 'bg-slate-800 text-slate-100 border-l-2 border-emerald-500 font-bold'
                  : 'text-slate-400 hover:bg-slate-800/50 hover:text-slate-200 border-l-2 border-transparent'
              }`}
            >
              <Icon className={`w-4 h-4 shrink-0 ${isSelected ? 'text-emerald-400' : 'text-slate-400 group-hover:text-slate-300'}`} />
              {!isCollapsed && (
                <span className="text-[10px] tracking-wider font-mono font-medium uppercase truncate">{item.label}</span>
              )}
            </button>
          );
        })}

        {/* PENDING ORDERS Accordion */}
        <div className="mt-2">
           <button
             onClick={() => {
               if (isCollapsed) setIsCollapsed(false);
               setIsPendingExpanded(!isPendingExpanded);
             }}
             title={isCollapsed ? "PENDING ORDERS" : undefined}
             className={`w-full flex items-center justify-between py-2.5 rounded-md select-none cursor-pointer transition-all duration-150 group ${
               isCollapsed ? 'justify-center px-0' : 'px-4'
             } ${
               isPendingExpanded
                 ? 'bg-slate-800 text-slate-100 border-l-2 border-amber-500 font-bold'
                 : 'text-slate-400 hover:bg-slate-800/50 hover:text-slate-200 border-l-2 border-transparent'
             }`}
           >
             <div className="flex items-center gap-3">
               <Clock className={`w-4 h-4 shrink-0 ${isPendingExpanded ? 'text-amber-500' : 'text-slate-400 group-hover:text-slate-300'}`} />
               {!isCollapsed && (
                 <span className="text-[10px] tracking-wider font-mono font-medium uppercase truncate flex items-center gap-2">
                   PENDING ORDERS
                   {pendingOrders.length > 0 && (
                     <span className="bg-amber-500/20 text-amber-500 px-1.5 py-0.5 rounded-full text-[9px] font-bold">
                       {pendingOrders.length}
                     </span>
                   )}
                 </span>
               )}
             </div>
             {!isCollapsed && (
               isPendingExpanded ? <ChevronUp className="w-3.5 h-3.5 text-slate-400" /> : <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
             )}
           </button>

           {/* Dropdown Content */}
           {isPendingExpanded && !isCollapsed && (
             <div className="mt-1 bg-slate-950/30 rounded-lg p-2 mx-2">
               {/* Search Bar */}
               <div className="relative mb-2">
                 <div className="absolute inset-y-0 left-0 pl-2 flex items-center pointer-events-none">
                   <Search className="w-3 h-3 text-slate-500" />
                 </div>
                 <input
                   type="text"
                   placeholder="Search sender, receiver..."
                   value={searchQuery}
                   onChange={(e) => setSearchQuery(e.target.value)}
                   className="w-full bg-slate-900 border border-slate-700 rounded pl-7 pr-2 py-1.5 text-[10px] text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500/50"
                 />
               </div>

               {/* List of Orders */}
               <div className="space-y-1.5 max-h-[35vh] overflow-y-auto pr-1">
                 {filteredOrders.length === 0 ? (
                    <div className="py-4 text-center">
                       <p className="text-[10px] text-slate-500">No matching orders.</p>
                    </div>
                 ) : (
                   filteredOrders.map(order => (
                     <button
                       key={order.id}
                       onClick={() => {
                         if (onSelectPendingOrder) onSelectPendingOrder(order);
                         setActiveTab('dashboard'); // ensure map is visible
                       }}
                       className={`w-full text-left p-2 rounded border transition-all duration-200 cursor-pointer ${
                         selectedPendingOrder?.id === order.id
                           ? 'bg-amber-900/20 border-amber-500/50'
                           : 'bg-slate-900 border-slate-800 hover:border-slate-700'
                       }`}
                     >
                       <div className="flex items-center justify-between mb-1">
                         <span className="text-[9px] font-mono text-slate-400">#{order.id.slice(0, 8)}</span>
                       </div>
                       <div className="space-y-1">
                         <div className="flex items-start gap-1.5">
                           <MapPin className="w-3 h-3 text-blue-400 shrink-0 mt-0.5" />
                           <div className="flex-1 min-w-0">
                             <p className="text-[10px] text-slate-300 truncate leading-tight">
                               {order.senderName || 'Sender'}
                             </p>
                             <p className="text-[9px] text-slate-500 truncate leading-tight">
                               {order.senderPhone || 'No phone'}
                             </p>
                           </div>
                         </div>
                         <div className="ml-1 pl-3 border-l border-slate-700/50 py-0.5">
                            <ArrowRight className="w-2.5 h-2.5 text-slate-600" />
                         </div>
                         <div className="flex items-start gap-1.5">
                           <MapPin className="w-3 h-3 text-emerald-400 shrink-0 mt-0.5" />
                           <div className="flex-1 min-w-0">
                             <p className="text-[10px] text-slate-300 truncate leading-tight">
                               {order.receiverName || 'Receiver'}
                             </p>
                             <p className="text-[9px] text-slate-500 truncate leading-tight">
                               {order.receiverPhone || 'No phone'}
                             </p>
                           </div>
                         </div>
                       </div>
                     </button>
                   ))
                 )}
               </div>
             </div>
           )}
        </div>
      </nav>

    </aside>
  );
}
