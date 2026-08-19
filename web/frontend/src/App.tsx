import { useState, useEffect, useRef } from 'react';
import L from 'leaflet';
import { io } from 'socket.io-client';
import Header from './components/Header';
import SidebarLeft from './components/SidebarLeft';
import MapContainer from './components/MapContainer';
import DeployModal from './components/DeployModal';
import TestingView from './components/TestingView';

import { AGV, LogEntry, MaintenanceRecord, Order } from './types';
import { INITIAL_AGVS, INITIAL_LOGS, INITIAL_MAINTENANCE, MUNICH_CENTER } from './data/mockData';

export default function App() {
  // Main Data States
  const [agvs, setAgvs] = useState<AGV[]>([]);
  const [selectedAgvId, setSelectedAgvId] = useState<string | null>(null);
  const [pendingOrders, setPendingOrders] = useState<any[]>([]);
  const [selectedPendingOrder, setSelectedPendingOrder] = useState<any>(null);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [records, setRecords] = useState<MaintenanceRecord[]>([]);

  // Global Config/UI States
  const [activeTab, setActiveTab] = useState<string>('dashboard');
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState<boolean>(false);
  const [trafficHeatmapActive, setTrafficHeatmapActive] = useState<boolean>(false);
  const [showDeployModal, setShowDeployModal] = useState<boolean>(false);
  const [simSpeed, setSimSpeed] = useState<number>(1);
  const [batteryThreshold, setBatteryThreshold] = useState<number>(20);
  const [isSimulating, setIsSimulating] = useState<boolean>(true);
  const [ordersTodayCount, setOrdersTodayCount] = useState<number>(84);

  // Map and Socket Instance References
  const mapRef = useRef<L.Map | null>(null);
  const socketRef = useRef<any>(null);
  const lowBatteryNotifiedRef = useRef<Record<string, boolean>>({});
  const orderStatusNotifiedRef = useRef<Record<string, string>>({});

  useEffect(() => {
    if ("Notification" in window && Notification.permission !== "granted" && Notification.permission !== "denied") {
      Notification.requestPermission();
    }
  }, []);

  // Custom logging helper
  const addLog = (message: string, type: 'info' | 'warning' | 'error' | 'success', agvId: string) => {
    const timestamp = new Date().toLocaleTimeString([], { hour12: false });
    const newLog: LogEntry = {
      id: `log-${Date.now()}-${Math.random()}`,
      timestamp,
      agvId,
      type,
      message,
    };
    setLogs((prev) => [newLog, ...prev]);
  };

  // Real-Time Socket Connection
  useEffect(() => {
    if (!isSimulating) return;

    // Connect to backend server dynamically based on where the web is accessed from
    const backendUrl = `${window.location.protocol}//${window.location.hostname}:3001`;
    const socket = io(backendUrl);
    socketRef.current = socket;

    socket.on('connect', () => {
      addLog(`Connected to backend telemetry server at ${backendUrl}.`, 'success', 'SYSTEM');
    });

    socket.on('disconnect', () => {
      addLog('Disconnected from backend server.', 'error', 'SYSTEM');
    });

    socket.on('agv_update', (agvData: AGV) => {
      // Check Battery Notification
      if (agvData.battery !== undefined && agvData.battery <= 30) {
        if (!lowBatteryNotifiedRef.current[agvData.id]) {
          lowBatteryNotifiedRef.current[agvData.id] = true;
          if ("Notification" in window && Notification.permission === "granted") {
            new Notification(`⚠️ Low Battery Alert: ${agvData.id}`, {
              body: `Battery dropped to ${agvData.battery}%. Please return to charging station.`,
            });
          }
        }
      } else if (agvData.battery !== undefined && agvData.battery > 30) {
        lowBatteryNotifiedRef.current[agvData.id] = false;
      }

      // Check Order Status Notification
      if (agvData.currentOrder && agvData.status) {
        const lastStatus = orderStatusNotifiedRef.current[agvData.id];
        if (lastStatus !== agvData.status) {
          orderStatusNotifiedRef.current[agvData.id] = agvData.status;
          if (['WAITING_SENDER', 'WAITING_RECEIVER', 'DONE', 'CANCELLED', 'LOCKED_AT_HOME', 'BLOCKED'].includes(agvData.status)) {
            if ("Notification" in window && Notification.permission === "granted") {
              let msg = `Order ${agvData.currentOrder.id} status changed to ${agvData.status}`;
              if (agvData.status === 'DONE') msg = `Order ${agvData.currentOrder.id} delivered successfully!`;
              else if (agvData.status === 'WAITING_SENDER') msg = `AGV arrived at sender. Ready to load.`;
              else if (agvData.status === 'WAITING_RECEIVER') msg = `AGV arrived at destination. Ready to unload.`;
              else if (agvData.status === 'CANCELLED') msg = `Order ${agvData.currentOrder.id} cancelled.`;
              else if (agvData.status === 'BLOCKED') msg = `AGV encountered an obstacle and is blocked!`;
              
              new Notification(`📦 AGV Update: ${agvData.id}`, { body: msg });
            }
          }
        }
      } else if (agvData.status === 'IDLE') {
        orderStatusNotifiedRef.current[agvData.id] = '';
      }

      setAgvs((prevAgvs) => {
        const exists = prevAgvs.find(a => a.id === agvData.id);
        if (exists) {
          return prevAgvs.map(a => a.id === agvData.id ? agvData : a);
        }
        
        // Auto-select the first AGV that connects
        if (prevAgvs.length === 0) {
          setSelectedAgvId(agvData.id);
        }
        
        return [...prevAgvs, agvData];
      });
    });

    socket.on('agv_log', (log: LogEntry) => {
      setLogs((prev) => [log, ...prev]);
    });

    socket.on('pending_orders', (orders: any[]) => {
      setPendingOrders(orders);
    });

    return () => {
      socket.disconnect();
      socketRef.current = null;
    };
  }, [isSimulating]);

  // Safety Controls callbacks
  const handleForceStop = (id: string) => {
    setAgvs((prev) =>
      prev.map((agv) => {
        if (agv.id === id) {
          addLog(`Safety shutdown initiated. ${id} EMERGENCY STOP engaged.`, 'error', id);
          return { ...agv, status: 'STOPPED', speed: 0 };
        }
        return agv;
      })
    );
  };

  const handleUnlock = (id: string) => {
    setAgvs((prev) =>
      prev.map((agv) => {
        if (agv.id === id && agv.status === 'STOPPED') {
          addLog(`Safety clearance verified. ${id} operations unlocked.`, 'success', id);
          const hasOrder = !!agv.currentOrder;
          return {
            ...agv,
            status: hasOrder ? 'DELIVERING' : 'IDLE',
            speed: hasOrder ? 1.2 : 0,
          };
        }
        return agv;
      })
    );
  };

  // Order assignment dispatch callback
  const handleAssignOrder = (id: string, order: Order) => {
    setAgvs((prev) =>
      prev.map((agv) => {
        if (agv.id === id) {
          addLog(`New Order #${order.id} dispatched to ${id}. Transit initiated.`, 'info', id);
          return {
            ...agv,
            status: 'DELIVERING',
            currentOrder: order,
            routeProgressIndex: 0,
            speed: 1.2,
          };
        }
        return agv;
      })
    );
  };

  const handleSendCommand = (cmd: any) => {
    if (socketRef.current) {
      socketRef.current.emit('agv_command', cmd);
      addLog(`Sent command ${cmd.action} to ${cmd.agvId}`, 'info', cmd.agvId);
    }
  };

  // Maintenance record lifecycle callbacks
  const handleStartRepair = (recordId: string) => {
    setRecords((prev) =>
      prev.map((r) => {
        if (r.id === recordId) {
          // Set target AGV status to Maintenance
          setAgvs((prevAgvs) =>
            prevAgvs.map((agv) => {
              if (agv.id === r.agvId) {
                return { ...agv, status: 'MAINTENANCE', speed: 0 };
              }
              return agv;
            })
          );
          addLog(`Mechanic dispatched. Repair started on ${r.agvId}.`, 'info', r.agvId);
          return { ...r, status: 'in_progress' };
        }
        return r;
      })
    );
  };

  const handleResolveIssue = (recordId: string) => {
    setRecords((prev) =>
      prev.map((r) => {
        if (r.id === recordId) {
          // Restore target AGV to Standby/Idle and reset health rating
          setAgvs((prevAgvs) =>
            prevAgvs.map((agv) => {
              if (agv.id === r.agvId) {
                return { ...agv, status: 'IDLE', maintenanceScore: 100 };
              }
              return agv;
            })
          );
          addLog(`Repair verified. ${r.agvId} system health diagnostics clear.`, 'success', r.agvId);
          return { ...r, status: 'completed' };
        }
        return r;
      })
    );
  };

  const handleAddRecord = (agvId: string, issue: string, priority: 'low' | 'medium' | 'high') => {
    const newRecord: MaintenanceRecord = {
      id: `maint-${Date.now()}`,
      agvId,
      issue,
      priority,
      reportedAt: new Date().toISOString().replace('T', ' ').slice(0, 16),
      status: 'pending',
      technician: priority === 'high' ? 'Marcus V.' : 'Sarah K.',
    };

    setRecords((prev) => [newRecord, ...prev]);

    // Decrease system health of the target AGV
    setAgvs((prevAgvs) =>
      prevAgvs.map((agv) => {
        if (agv.id === agvId) {
          const nextScore = Math.max(0, agv.maintenanceScore - 15);
          return { ...agv, maintenanceScore: nextScore };
        }
        return agv;
      })
    );

    addLog(`Hardware ticket reported for ${agvId}: ${issue}`, 'warning', agvId);
  };

  // Deploying new AGV callback
  const handleDeploy = (newAgv: AGV) => {
    setAgvs((prev) => [...prev, newAgv]);
    setSelectedAgvId(newAgv.id);
    setShowDeployModal(false);
    addLog(`New vehicle ${newAgv.id} (${newAgv.name}) deployed into active fleet operations.`, 'success', newAgv.id);
  };

  // Find currently selected AGV structure
  const selectedAgv = agvs.find((a) => a.id === selectedAgvId) || null;

  // Global counts
  const activeRobotsCount = agvs.filter((a) => a.status !== 'STOPPED' && a.status !== 'MAINTENANCE' && a.status !== 'OFFLINE').length;
  const agvIds = agvs.map((a) => a.id);

  return (
    <div className="min-h-screen relative overflow-hidden bg-slate-950 text-slate-100 font-sans antialiased">
      
      {/* Top Banner Header controls */}
      <Header
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        activeRobotsCount={activeRobotsCount}
        ordersTodayCount={ordersTodayCount}
        logs={logs}
        simSpeed={simSpeed}
        setSimSpeed={setSimSpeed}
        batteryThreshold={batteryThreshold}
        setBatteryThreshold={setBatteryThreshold}
        isSimulating={isSimulating}
        setIsSimulating={setIsSimulating}
      />

      {/* Lefthand Nav Sidebar */}
      <SidebarLeft
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        activeRobotsCount={activeRobotsCount}
        onDeployClick={() => setShowDeployModal(true)}
        isCollapsed={isSidebarCollapsed}
        setIsCollapsed={setIsSidebarCollapsed}
        pendingOrders={pendingOrders}
        selectedPendingOrder={selectedPendingOrder}
        onSelectPendingOrder={setSelectedPendingOrder}
      />

      {/* Main Map Content Layer */}
      <div className={`fixed inset-0 pt-12 transition-all duration-300 ${isSidebarCollapsed ? 'pl-20' : 'pl-80'} ${activeTab === 'dashboard' || activeTab === 'pending-orders' ? 'opacity-100' : 'opacity-10 pointer-events-none'}`}>
        <MapContainer
          agvs={agvs}
          selectedAgvId={selectedAgvId}
          setSelectedAgvId={setSelectedAgvId}
          trafficHeatmapActive={trafficHeatmapActive}
          mapRef={mapRef}
          onSendCommand={handleSendCommand}
          selectedPendingOrder={selectedPendingOrder}
        />

        {/* Map Controls (Bottom Right overlaying Map View) */}
        <div className="absolute bottom-6 right-6 flex gap-3 z-[1000] select-none">
          <div className="bg-slate-900 border border-slate-800 p-1.5 rounded-md flex gap-1.5 shadow-2xl">
            <button
              onClick={() => mapRef.current?.zoomIn()}
              className="w-8 h-8 flex items-center justify-center rounded bg-slate-800 text-slate-300 hover:bg-slate-700 cursor-pointer border border-slate-700 active:scale-95"
              title="Zoom In"
            >
              <span className="material-symbols-outlined font-bold text-sm">add</span>
            </button>
            <button
              onClick={() => mapRef.current?.zoomOut()}
              className="w-8 h-8 flex items-center justify-center rounded bg-slate-800 text-slate-300 hover:bg-slate-700 cursor-pointer border border-slate-700 active:scale-95"
              title="Zoom Out"
            >
              <span className="material-symbols-outlined font-bold text-sm">remove</span>
            </button>
          </div>

          <div className="bg-slate-900 border border-slate-800 p-1.5 rounded-md flex gap-1.5 shadow-2xl">
            <button
              onClick={() => setTrafficHeatmapActive(!trafficHeatmapActive)}
              className={`px-3 h-8 flex items-center gap-1.5 rounded font-mono text-[10px] uppercase tracking-wider transition-all cursor-pointer border ${
                trafficHeatmapActive
                  ? 'bg-emerald-600 text-white border-emerald-500 font-bold'
                  : 'bg-slate-800 text-slate-400 border-slate-700 hover:bg-slate-700'
              }`}
            >
              <span className="material-symbols-outlined text-xs font-bold">layers</span>
              <span>Traffic Heatmap</span>
            </button>
            <button
              onClick={() => {
                if (agvs.length > 0) {
                  mapRef.current?.flyTo([agvs[0].lat, agvs[0].lng], 18);
                }
              }}
              className="px-3 h-8 flex items-center gap-1.5 rounded bg-slate-800 text-slate-300 font-mono text-[10px] uppercase tracking-wider border border-slate-700 hover:bg-slate-700 cursor-pointer active:scale-95"
            >
              <span className="material-symbols-outlined text-xs">navigation</span>
              <span>Recenter</span>
            </button>
          </div>
        </div>
      </div>



      {/* Tab Panels overlays in central workspace */}

      {activeTab === 'testing' && (
        <TestingView 
          agvs={agvs} 
          selectedAgvId={selectedAgvId} 
          onSendCommand={handleSendCommand} 
          isSidebarCollapsed={isSidebarCollapsed} 
          logs={logs}
        />
      )}



      {/* Deploy modal overlay */}
      {showDeployModal && (
        <DeployModal
          onClose={() => setShowDeployModal(false)}
          onDeploy={handleDeploy}
          existingCount={agvs.length}
        />
      )}

    </div>
  );
}
