import { Zap, Wrench, ShieldAlert, Cpu, CheckCircle } from 'lucide-react';
import { AGV } from '../types';

interface FleetHealthViewProps {
  agvs: AGV[];
  onSelectAgv: (id: string) => void;
  setActiveTab: (tab: string) => void;
  isSidebarCollapsed: boolean;
}

export default function FleetHealthView({ agvs, onSelectAgv, setActiveTab, isSidebarCollapsed }: FleetHealthViewProps) {
  // Aggregate stats
  const totalAgvs = agvs.length;
  const avgBattery = Math.round(agvs.reduce((acc, curr) => acc + curr.battery, 0) / totalAgvs);
  const avgMaintenance = Math.round(agvs.reduce((acc, curr) => acc + curr.maintenanceScore, 0) / totalAgvs);
  const criticalBatteryCount = agvs.filter((a) => a.battery < 30).length;
  const criticalMaintCount = agvs.filter((a) => a.maintenanceScore < 60).length;

  const handleRowClick = (id: string) => {
    onSelectAgv(id);
    setActiveTab('dashboard'); // Jump to map view
  };

  return (
    <div className={`absolute inset-y-0 right-0 top-12 bottom-0 overflow-y-auto p-6 bg-slate-950 z-30 flex flex-col space-y-6 text-slate-200 transition-all duration-300 ${isSidebarCollapsed ? 'left-20' : 'left-64'}`}>
      
      {/* Page Title & Breadcrumbs */}
      <div>
        <h1 className="text-base font-bold uppercase tracking-wider text-slate-200 font-mono">Fleet Health Diagnostics</h1>
        <p className="text-[11px] text-slate-500 mt-0.5">Real-time status diagnostics, hardware ratings, and charge levels across all deployed units.</p>
      </div>

      {/* Bento Grid Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {/* Fleet Count */}
        <div className="bg-slate-900 border border-slate-800 p-4 rounded-md flex items-center justify-between shadow-2xl">
          <div>
            <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block font-mono">Fleet Size</span>
            <span className="text-2xl font-bold text-slate-100 block mt-1 font-mono">{totalAgvs} UNITS</span>
            <span className="text-[9px] text-slate-500 block mt-0.5 uppercase font-mono">All models synchronized</span>
          </div>
          <div className="w-10 h-10 rounded bg-slate-800 border border-slate-700 text-slate-400 flex items-center justify-center">
            <Cpu className="w-5 h-5" />
          </div>
        </div>

        {/* Average Battery */}
        <div className="bg-slate-900 border border-slate-800 p-4 rounded-md flex items-center justify-between shadow-2xl">
          <div>
            <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block font-mono">Average Battery</span>
            <span className="text-2xl font-bold text-emerald-400 block mt-1 font-mono">{avgBattery}%</span>
            <span className="text-[9px] text-slate-500 block mt-0.5 uppercase font-mono">
              {criticalBatteryCount > 0 ? (
                <span className="text-rose-400 font-bold">{criticalBatteryCount} LOW CHARGE UNITS</span>
              ) : (
                'ALL DOCKED / ACTIVE UNITS SAFE'
              )}
            </span>
          </div>
          <div className="w-10 h-10 rounded bg-slate-800 border border-slate-700 text-emerald-400 flex items-center justify-center">
            <Zap className="w-5 h-5" />
          </div>
        </div>

        {/* Average System Health */}
        <div className="bg-slate-900 border border-slate-800 p-4 rounded-md flex items-center justify-between shadow-2xl">
          <div>
            <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block font-mono">System Health</span>
            <span className="text-2xl font-bold text-slate-100 block mt-1 font-mono">{avgMaintenance}%</span>
            <span className="text-[9px] text-slate-500 block mt-0.5 uppercase font-mono">
              {criticalMaintCount > 0 ? (
                <span className="text-amber-400 font-bold">{criticalMaintCount} DEGRADED RATINGS</span>
              ) : (
                'HARDWARE PROFILES WITHIN SPEC'
              )}
            </span>
          </div>
          <div className="w-10 h-10 rounded bg-slate-800 border border-slate-700 text-slate-400 flex items-center justify-center">
            <Wrench className="w-5 h-5" />
          </div>
        </div>

        {/* Critical Interventions */}
        <div className="bg-slate-900 border border-slate-800 p-4 rounded-md flex items-center justify-between shadow-2xl">
          <div>
            <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block font-mono">Interventions</span>
            <span className={`text-2xl font-bold block mt-1 font-mono ${criticalBatteryCount + criticalMaintCount > 0 ? 'text-rose-400' : 'text-emerald-400'}`}>
              {criticalBatteryCount + criticalMaintCount} ISSUES
            </span>
            <span className="text-[9px] text-slate-500 block mt-0.5 uppercase font-mono">
              {criticalBatteryCount + criticalMaintCount > 0 ? 'ATTENTION REQUIRED' : 'INTEGRITY SECURE'}
            </span>
          </div>
          <div className={`w-10 h-10 rounded flex items-center justify-center border ${criticalBatteryCount + criticalMaintCount > 0 ? 'bg-rose-500/10 text-rose-400 border-rose-500/20' : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'}`}>
            {criticalBatteryCount + criticalMaintCount > 0 ? <ShieldAlert className="w-5 h-5" /> : <CheckCircle className="w-5 h-5" />}
          </div>
        </div>
      </div>

      {/* Main Registry Directory */}
      <div className="bg-slate-900 border border-slate-800 rounded-md p-4 shadow-2xl overflow-hidden">
        <h3 className="text-xs font-bold text-slate-300 tracking-wider uppercase mb-3 font-mono">Autonomous Guided Vehicle Registry</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400 text-[10px] uppercase font-mono">
                <th className="py-2 px-3 font-bold">ID</th>
                <th className="py-2 px-3 font-bold">Nickname</th>
                <th className="py-2 px-3 font-bold">Classification</th>
                <th className="py-2 px-3 font-bold text-center">Power</th>
                <th className="py-2 px-3 font-bold text-center">Health Rating</th>
                <th className="py-2 px-3 font-bold">Status</th>
                <th className="py-2 px-3 font-bold text-right">Accumulated Range</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-xs">
              {agvs.map((agv) => {
                const isBatteryLow = agv.battery < 30;
                const isHealthPoor = agv.maintenanceScore < 60;

                return (
                  <tr
                    key={agv.id}
                    onClick={() => handleRowClick(agv.id)}
                    className="hover:bg-slate-800/60 cursor-pointer transition-colors duration-200 group"
                  >
                    <td className="py-2.5 px-3 font-bold text-slate-300 font-mono group-hover:text-emerald-400">{agv.id}</td>
                    <td className="py-2.5 px-3 text-slate-200">{agv.name}</td>
                    <td className="py-2.5 px-3 text-slate-400 uppercase font-mono text-[10px]">{agv.type}</td>
                    <td className="py-2.5 px-3 text-center">
                      <span className={`font-bold font-mono ${isBatteryLow ? 'text-rose-400' : 'text-emerald-400'}`}>
                        {agv.battery}%
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-center">
                      <span className={`font-mono ${isHealthPoor ? 'text-rose-400' : 'text-slate-300'}`}>
                        {agv.maintenanceScore}%
                      </span>
                    </td>
                    <td className="py-2.5 px-3">
                      <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold uppercase border ${
                        agv.status === 'DELIVERING'
                          ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                          : agv.status === 'IDLE'
                          ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                          : agv.status === 'CHARGING'
                          ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20'
                          : agv.status === 'STOPPED'
                          ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                          : 'bg-slate-500/10 text-slate-400 border border-slate-500/20'
                      }`}>
                        {agv.status}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-right text-slate-400 font-mono">
                      {agv.totalDistanceTraveled.toFixed(1)} km
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
