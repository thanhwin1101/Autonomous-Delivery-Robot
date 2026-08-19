import { useState } from 'react';
import { Bell, Settings, ShieldAlert, CheckCircle2, Sliders, Cpu, Activity, Play, Pause } from 'lucide-react';
import { LogEntry } from '../types';

interface HeaderProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
  activeRobotsCount: number;
  ordersTodayCount: number;
  logs: LogEntry[];
  simSpeed: number;
  setSimSpeed: (speed: number) => void;
  batteryThreshold: number;
  setBatteryThreshold: (val: number) => void;
  isSimulating: boolean;
  setIsSimulating: (val: boolean) => void;
}

export default function Header({
  activeTab,
  setActiveTab,
  activeRobotsCount,
  ordersTodayCount,
  logs,
  simSpeed,
  setSimSpeed,
  batteryThreshold,
  setBatteryThreshold,
  isSimulating,
  setIsSimulating,
}: HeaderProps) {
  const [showNotifications, setShowNotifications] = useState(false);
  const [showSettings, setShowSettings] = useState(false);

  // Filter logs to display as notifications (e.g. warnings, errors, successes)
  const notificationLogs = logs.slice(0, 5);

  return (
    <header className="fixed top-0 left-0 w-full z-50 flex justify-between items-center px-4 h-12 bg-slate-900 border-b border-slate-800 font-sans text-slate-200 tracking-tight select-none">
      <div className="flex items-center gap-6">
        {/* Brand Logo matching High Density specifications */}
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-emerald-500 animate-pulse"></div>
          <span className="font-bold tracking-tight uppercase text-xs text-slate-200">
            FleetManager <span className="text-slate-500 font-mono font-normal">v2.4.0</span>
          </span>
        </div>


      </div>

      <div className="flex items-center gap-4">
        {/* Live Counters */}
        <div className="flex items-center gap-4 px-3 py-1 bg-slate-950/40 rounded border border-slate-800/80 text-[11px] font-mono">
          <div className="flex items-center gap-2">
            <span className="text-slate-500">ACTIVE:</span>
            <span className="text-emerald-400 font-bold">{activeRobotsCount}</span>
          </div>
          <div className="w-px h-3 bg-slate-800" />
          <div className="flex items-center gap-2">
            <span className="text-slate-500">COMPLETED:</span>
            <span className="text-emerald-400 font-bold">{ordersTodayCount}</span>
          </div>
        </div>

        {/* Action Button Center */}
        <div className="flex items-center gap-1.5 relative">
          {/* Notifications Trigger */}
          <button
            onClick={() => {
              setShowNotifications(!showNotifications);
              setShowSettings(false);
            }}
            className={`p-1.5 rounded cursor-pointer transition-colors relative ${
              showNotifications
                ? 'bg-slate-800 text-white'
                : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
            }`}
          >
            <Bell className="w-4 h-4" />
            <span className="absolute top-1 right-1 w-1.5 h-1.5 rounded-full bg-emerald-500" />
          </button>

          {/* Settings Trigger */}
          <button
            onClick={() => {
              setShowSettings(!showSettings);
              setShowNotifications(false);
            }}
            className={`p-1.5 rounded cursor-pointer transition-colors relative ${
              showSettings
                ? 'bg-slate-800 text-white'
                : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
            }`}
          >
            <Settings className="w-4 h-4" />
          </button>

          {/* Notifications Dropdown */}
          {showNotifications && (
            <div className="absolute right-10 top-11 w-80 bg-slate-900 border border-slate-800 rounded-md p-4 shadow-2xl z-50 text-slate-200">
              <div className="flex items-center justify-between border-b border-slate-800 pb-2 mb-2">
                <span className="font-bold text-[10px] text-slate-400 tracking-wider uppercase font-mono">Live Activity Logs</span>
                <span className="text-[10px] text-slate-500 font-mono">RECENT 5</span>
              </div>
              <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                {notificationLogs.map((log) => (
                  <div key={log.id} className="text-[11px] flex items-start gap-2 p-1.5 hover:bg-slate-800/60 rounded transition-colors">
                    {log.type === 'error' || log.type === 'warning' ? (
                      <ShieldAlert className="w-3.5 h-3.5 text-rose-500 shrink-0 mt-0.5" />
                    ) : (
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-0.5" />
                    )}
                    <div>
                      <p className="font-medium text-slate-300 leading-tight">{log.message}</p>
                      <span className="text-[9px] font-mono text-slate-500">{log.timestamp} • {log.agvId}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Quick Settings Panel Dropdown */}
          {showSettings && (
            <div className="absolute right-0 top-11 w-72 bg-slate-900 border border-slate-800 rounded-md p-4 shadow-2xl z-50 text-slate-200">
              <div className="flex items-center gap-1.5 border-b border-slate-800 pb-2 mb-3">
                <Sliders className="w-3.5 h-3.5 text-slate-400" />
                <span className="font-bold text-[10px] text-slate-400 tracking-wider uppercase font-mono">Simulation Controls</span>
              </div>
              <div className="space-y-4">
                {/* Speed Multiplier Slider */}
                <div className="space-y-1">
                  <div className="flex justify-between text-[11px] font-mono">
                    <span className="text-slate-400">SIM SPEED</span>
                    <span className="text-emerald-400 font-bold">{simSpeed}x</span>
                  </div>
                  <input
                    type="range"
                    min="1"
                    max="10"
                    step="1"
                    value={simSpeed}
                    onChange={(e) => setSimSpeed(Number(e.target.value))}
                    className="w-full h-1 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-emerald-500"
                  />
                  <p className="text-[9px] text-slate-500 leading-tight">Increments the rate of coordinate transition and battery discharge.</p>
                </div>

                {/* Auto charge trigger threshold */}
                <div className="space-y-1">
                  <div className="flex justify-between text-[11px] font-mono">
                    <span className="text-slate-400">AUTO-CHARGE</span>
                    <span className="text-amber-500 font-bold">{batteryThreshold}%</span>
                  </div>
                  <input
                    type="range"
                    min="10"
                    max="40"
                    step="5"
                    value={batteryThreshold}
                    onChange={(e) => setBatteryThreshold(Number(e.target.value))}
                    className="w-full h-1 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-amber-500"
                  />
                  <p className="text-[9px] text-slate-500 leading-tight">Vehicles will automatically route to charging bays below this level.</p>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* User Profile avatar */}
        <div className="w-6 h-6 rounded-full border border-slate-700 overflow-hidden select-none hover:border-slate-500 transition-colors">
          <img
            alt="User Profile"
            className="w-full h-full object-cover"
            src="https://lh3.googleusercontent.com/aida-public/AB6AXuAE5AMmC_2MGp4eYydyUTCN3fxUez5Cui82_yFtF0QKx_poMAGgzLHUHHir6CMs89o_zp4Mz7oQgcgw-yMmikqJmDxXeb-xum7av8qMbdtE2Om7Bbx2ZkX3c-Lro3lpy12LYQF29rQMTcNOjk7PtPc3KSgnKwBIgc55b7VWF2KlyuaGzC6S55TKqe7TxAEebf39YH80Me8ZVeKTvbi5001cd-9ZR1q5isN54nbMRheQG88XU8tO-eBYcQ"
          />
        </div>
      </div>
    </header>
  );
}
