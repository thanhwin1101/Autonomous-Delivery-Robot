import { useState, FormEvent } from 'react';
import { Search, Filter, ShieldAlert, CheckCircle2, Info, PlusCircle, Trash2 } from 'lucide-react';
import { LogEntry } from '../types';

interface DispatchLogsViewProps {
  logs: LogEntry[];
  onAddLog: (msg: string, type: 'info' | 'warning' | 'error' | 'success', agvId: string) => void;
  onClearLogs: () => void;
  agvIds: string[];
  isSidebarCollapsed: boolean;
}

export default function DispatchLogsView({
  logs,
  onAddLog,
  onClearLogs,
  agvIds,
  isSidebarCollapsed,
}: DispatchLogsViewProps) {
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState<'all' | 'info' | 'warning' | 'error' | 'success'>('all');

  // Form states for manual log injection
  const [customMsg, setCustomMsg] = useState('');
  const [customType, setCustomType] = useState<'info' | 'warning' | 'error' | 'success'>('info');
  const [customAgv, setCustomAgv] = useState(agvIds[0] || 'AGV-01');

  // Filtered Logs calculation
  const filteredLogs = logs.filter((log) => {
    const matchesSearch = log.message.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          log.agvId.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesType = typeFilter === 'all' || log.type === typeFilter;
    return matchesSearch && matchesType;
  });

  const handleInjectLog = (e: FormEvent) => {
    e.preventDefault();
    if (!customMsg.trim()) return;

    onAddLog(customMsg.trim(), customType, customAgv);
    setCustomMsg('');
  };

  return (
    <div className={`absolute inset-y-0 right-0 top-12 bottom-0 overflow-y-auto p-6 bg-slate-950 z-30 flex flex-col md:flex-row gap-6 text-slate-200 transition-all duration-300 ${isSidebarCollapsed ? 'left-20' : 'left-64'}`}>
      
      {/* Left Column: Logs Stream */}
      <div className="flex-1 flex flex-col space-y-4">
        <div>
          <h1 className="text-base font-bold uppercase tracking-wider text-slate-200 font-mono">Dispatch Log Monitor</h1>
          <p className="text-[11px] text-slate-500 mt-0.5">Live streaming feed of automated vehicle reports, transit telemetry, and dock sensor signals.</p>
        </div>

        {/* Toolbar Controls */}
        <div className="flex flex-col sm:flex-row gap-4 justify-between items-center bg-slate-900 p-3 rounded-md border border-slate-800">
          
          {/* Search bar */}
          <div className="relative w-full sm:w-56">
            <Search className="w-3.5 h-3.5 text-slate-500 absolute left-2.5 top-2" />
            <input
              type="text"
              placeholder="Search vehicles / tags..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-8 pr-3 py-1 bg-slate-950 border border-slate-800 rounded text-xs text-slate-300 focus:outline-none focus:border-slate-700 transition-colors placeholder-slate-700 font-mono"
            />
          </div>

          {/* Type Filters */}
          <div className="flex items-center gap-1 flex-wrap">
            {(['all', 'info', 'success', 'warning', 'error'] as const).map((filter) => (
              <button
                key={filter}
                onClick={() => setTypeFilter(filter)}
                className={`px-2 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider cursor-pointer border transition-all font-mono ${
                  typeFilter === filter
                    ? 'bg-slate-800 border-slate-700 text-slate-200'
                    : 'bg-slate-950 border-slate-900 text-slate-500 hover:text-slate-300'
                }`}
              >
                {filter}
              </button>
            ))}
          </div>

          {/* Reset Logs Action */}
          <button
            onClick={onClearLogs}
            className="flex items-center gap-1.5 px-2 py-1 border border-rose-500/20 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 rounded text-[9px] font-bold uppercase tracking-wider cursor-pointer transition-colors font-mono"
          >
            <Trash2 className="w-3.5 h-3.5" />
            Clear
          </button>
        </div>

        {/* Event Logs List Container */}
        <div className="bg-slate-900 border border-slate-800 rounded-md flex-1 p-4 shadow-2xl flex flex-col overflow-hidden">
          <div className="overflow-y-auto flex-1 pr-2 space-y-2 max-h-[480px]">
            {filteredLogs.length > 0 ? (
              filteredLogs.map((log) => (
                <div
                  key={log.id}
                  className={`p-2.5 rounded border flex items-start gap-3 transition-colors duration-200 hover:bg-slate-800/40 ${
                    log.type === 'error'
                      ? 'bg-rose-500/5 border-rose-500/20'
                      : log.type === 'warning'
                      ? 'bg-amber-500/5 border-amber-500/20'
                      : log.type === 'success'
                      ? 'bg-emerald-500/5 border-emerald-500/20'
                      : 'bg-[#111827]/30 border-slate-800/80'
                  }`}
                >
                  <div className="shrink-0 mt-0.5">
                    {log.type === 'error' && <ShieldAlert className="w-3.5 h-3.5 text-rose-400" />}
                    {log.type === 'warning' && <ShieldAlert className="w-3.5 h-3.5 text-amber-400" />}
                    {log.type === 'success' && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />}
                    {log.type === 'info' && <Info className="w-3.5 h-3.5 text-slate-400" />}
                  </div>
                  <div className="flex-1">
                    <p className="text-[11px] text-slate-300 leading-normal font-sans">{log.message}</p>
                    <div className="flex items-center gap-1.5 mt-1 text-[9px] text-slate-500 font-mono">
                      <span>{log.timestamp}</span>
                      <span>•</span>
                      <span className="text-slate-300 font-bold">{log.agvId}</span>
                    </div>
                  </div>
                </div>
              ))
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-center p-6 select-none">
                <Trash2 className="w-8 h-8 text-slate-700 mb-2" />
                <p className="text-[11px] text-slate-400 font-bold">No event logs matching selection</p>
                <p className="text-[9px] text-slate-600 mt-1">Adjust filters or search metrics to reveal items.</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Right Column: Custom Log Event Injector Form */}
      <div className="w-full md:w-80 flex flex-col space-y-4">
        <div>
          <h2 className="text-sm font-bold text-slate-200 uppercase tracking-wider font-mono select-none">Simulator Event Injector</h2>
          <p className="text-[11px] text-slate-500 mt-0.5">Simulate obstacles, diagnostic alerts, or status events instantly to audit fleet control responses.</p>
        </div>

        <div className="bg-slate-900 border border-slate-800 p-4 rounded-md shadow-2xl">
          <form onSubmit={handleInjectLog} className="space-y-4">
            {/* Target AGV selector */}
            <div className="space-y-1">
              <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Target Vehicle</label>
              <select
                value={customAgv}
                onChange={(e) => setCustomAgv(e.target.value)}
                className="w-full mt-1 px-3 py-1.5 bg-slate-950 border border-slate-800 rounded text-xs text-slate-200 focus:outline-none focus:border-slate-700 font-mono"
              >
                {agvIds.map((id) => (
                  <option key={id} value={id}>
                    {id}
                  </option>
                ))}
              </select>
            </div>

            {/* Severity level */}
            <div className="space-y-1">
              <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Severity Level</label>
              <div className="grid grid-cols-2 gap-2 mt-1">
                {(['info', 'success', 'warning', 'error'] as const).map((type) => (
                  <button
                    key={type}
                    type="button"
                    onClick={() => setCustomType(type)}
                    className={`py-1 rounded text-[10px] font-bold uppercase border transition-all cursor-pointer font-mono ${
                      customType === type
                        ? type === 'error'
                          ? 'bg-rose-500/10 border-rose-500 text-rose-400'
                          : type === 'warning'
                          ? 'bg-amber-500/10 border-amber-500 text-amber-400'
                          : type === 'success'
                          ? 'bg-emerald-500/10 border-emerald-500 text-emerald-400'
                          : 'bg-blue-500/10 border-blue-500 text-blue-400'
                        : 'bg-slate-950 border-slate-800 text-slate-500 hover:text-slate-300'
                    }`}
                  >
                    {type}
                  </button>
                ))}
              </div>
            </div>

            {/* Event message description */}
            <div className="space-y-1">
              <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Event Message</label>
              <textarea
                required
                rows={3}
                placeholder="e.g. Laser rangefinder beam blocked in Sector E3."
                value={customMsg}
                onChange={(e) => setCustomMsg(e.target.value)}
                className="w-full mt-1 p-2.5 bg-slate-950 border border-slate-800 rounded text-xs text-slate-200 focus:outline-none focus:border-slate-700 transition-colors placeholder-slate-700 resize-none font-mono"
              />
            </div>

            {/* Trigger Button */}
            <button
              type="submit"
              className="w-full py-2 bg-slate-800 hover:bg-slate-700 text-slate-100 border border-slate-700 font-bold rounded text-[10px] uppercase tracking-wider transition-all flex items-center justify-center gap-1.5 cursor-pointer font-mono"
            >
              <PlusCircle className="w-4 h-4" />
              <span>Broadcast Event</span>
            </button>
          </form>
        </div>
      </div>

    </div>
  );
}
