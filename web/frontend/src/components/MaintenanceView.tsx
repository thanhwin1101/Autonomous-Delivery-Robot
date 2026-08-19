import { useState, FormEvent } from 'react';
import { Wrench, Clock, CheckCircle, Plus, User, AlertCircle, Sparkles } from 'lucide-react';
import { MaintenanceRecord, AGV } from '../types';

interface MaintenanceViewProps {
  records: MaintenanceRecord[];
  agvs: AGV[];
  onStartRepair: (recordId: string) => void;
  onResolveIssue: (recordId: string) => void;
  onAddRecord: (agvId: string, issue: string, priority: 'low' | 'medium' | 'high') => void;
  isSidebarCollapsed: boolean;
}

export default function MaintenanceView({
  records,
  agvs,
  onStartRepair,
  onResolveIssue,
  onAddRecord,
  isSidebarCollapsed,
}: MaintenanceViewProps) {
  const [selectedAgvId, setSelectedAgvId] = useState(agvs[0]?.id || 'AGV-01');
  const [issueText, setIssueText] = useState('');
  const [priority, setPriority] = useState<'low' | 'medium' | 'high'>('medium');

  const handleSubmitIssue = (e: FormEvent) => {
    e.preventDefault();
    if (!issueText.trim()) return;

    onAddRecord(selectedAgvId, issueText.trim(), priority);
    setIssueText('');
  };

  // Color config for priority level tags
  const priorityColors = {
    high: 'bg-rose-500/15 text-rose-400 border-rose-500/20',
    medium: 'bg-amber-500/15 text-amber-400 border-amber-500/20',
    low: 'bg-sky-500/15 text-sky-400 border-sky-500/20',
  };

  // Color config for status tags
  const statusColors = {
    pending: 'bg-amber-500/10 text-amber-400 border border-amber-500/20',
    in_progress: 'bg-purple-500/10 text-purple-400 border border-purple-500/20',
    completed: 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20',
  };

  return (
    <div className={`absolute inset-y-0 right-0 top-12 bottom-0 overflow-y-auto p-6 bg-slate-950 z-30 flex flex-col lg:flex-row gap-6 text-slate-200 transition-all duration-300 ${isSidebarCollapsed ? 'left-20' : 'left-64'}`}>
      
      {/* Left Column: Maintenance Tickets & Log */}
      <div className="flex-1 flex flex-col space-y-4">
        <div>
          <h1 className="text-base font-bold uppercase tracking-wider text-slate-200 font-mono">Maintenance & Diagnostics</h1>
          <p className="text-[11px] text-slate-500 mt-0.5">Track physical component wear, log sensor faults, and dispatch on-site mechanics for hardware adjustments.</p>
        </div>

        {/* Tickets Grid list */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {records.map((record) => {
            const targetAgv = agvs.find((a) => a.id === record.agvId);
            return (
              <div
                key={record.id}
                className="bg-slate-900 p-4 rounded-md flex flex-col justify-between border border-slate-800 relative hover:border-slate-700 transition-all duration-200 shadow-2xl"
              >
                {/* Header info */}
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-slate-200 text-xs flex items-center gap-1.5 font-mono">
                      <Wrench className="w-3.5 h-3.5 text-slate-400" />
                      {record.agvId}
                    </span>
                    <div className="flex items-center gap-2">
                      <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider border ${priorityColors[record.priority]}`}>
                        {record.priority}
                      </span>
                      <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider border ${statusColors[record.status]}`}>
                        {record.status.replace('_', ' ')}
                      </span>
                    </div>
                  </div>

                  <p className="text-[11px] text-slate-300 leading-relaxed min-h-[40px]">
                    {record.issue}
                  </p>
                </div>

                {/* Footer and interactions */}
                <div className="border-t border-slate-800 pt-3 mt-3 flex items-center justify-between text-[10px] text-slate-500">
                  <div className="flex items-center gap-1.5">
                    <User className="w-3.5 h-3.5 text-slate-600" />
                    <span>Tech: {record.technician || 'Unassigned'}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    {record.status === 'pending' && (
                      <button
                        onClick={() => onStartRepair(record.id)}
                        className="px-2 py-0.5 bg-purple-500/10 border border-purple-500/20 hover:bg-purple-500/20 text-purple-400 font-bold rounded text-[9px] uppercase cursor-pointer transition-all"
                      >
                        Start Repair
                      </button>
                    )}
                    {record.status === 'in_progress' && (
                      <button
                        onClick={() => onResolveIssue(record.id)}
                        className="px-2 py-0.5 bg-emerald-500/10 border border-emerald-500/20 hover:bg-emerald-500/20 text-emerald-400 font-bold rounded text-[9px] uppercase cursor-pointer transition-all flex items-center gap-1"
                      >
                        <CheckCircle className="w-3 h-3" />
                        Resolve
                      </button>
                    )}
                    {record.status === 'completed' && (
                      <span className="text-emerald-400 font-bold flex items-center gap-1">
                        <CheckCircle className="w-3.5 h-3.5" />
                        Resolved
                      </span>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Right Column: Diagnostic Form */}
      <div className="w-full lg:w-80 flex flex-col space-y-4 shrink-0">
        <div>
          <h2 className="text-sm font-bold text-slate-200 uppercase tracking-wider font-mono select-none">Log Diagnostic Issue</h2>
          <p className="text-[11px] text-slate-500 mt-0.5">Initiate physical hardware work tickets or report navigation optical errors directly into the queue.</p>
        </div>

        <div className="bg-slate-900 p-4 rounded-md border border-slate-800 shadow-2xl">
          <form onSubmit={handleSubmitIssue} className="space-y-4">
            
            {/* Target AGV selection */}
            <div className="space-y-1">
              <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Target Vehicle</label>
              <select
                value={selectedAgvId}
                onChange={(e) => setSelectedAgvId(e.target.value)}
                className="w-full mt-1 px-3 py-1.5 bg-slate-950 border border-slate-800 rounded text-xs text-slate-200 focus:outline-none focus:border-slate-700 font-mono"
              >
                {agvs.map((agv) => (
                  <option key={agv.id} value={agv.id}>
                    {agv.id} - {agv.name}
                  </option>
                ))}
              </select>
            </div>

            {/* Priority levels selection */}
            <div className="space-y-1">
              <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Report Priority</label>
              <div className="grid grid-cols-3 gap-2 mt-1">
                {(['low', 'medium', 'high'] as const).map((p) => (
                  <button
                    key={p}
                    type="button"
                    onClick={() => setPriority(p)}
                    className={`py-1 rounded text-[10px] font-bold uppercase border transition-all cursor-pointer font-mono ${
                      priority === p
                        ? p === 'high'
                          ? 'bg-rose-500/10 border-rose-500 text-rose-400'
                          : p === 'medium'
                          ? 'bg-amber-500/10 border-amber-500 text-amber-400'
                          : 'bg-blue-500/10 border-blue-500 text-blue-400'
                        : 'bg-slate-950 border-slate-800 text-slate-500 hover:text-slate-300'
                    }`}
                  >
                    {p}
                  </button>
                ))}
              </div>
            </div>

            {/* Issue Description */}
            <div className="space-y-1">
              <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Fault Details</label>
              <textarea
                required
                rows={4}
                placeholder="Describe sensor anomalies, steering resistance, or mechanical issues..."
                value={issueText}
                onChange={(e) => setIssueText(e.target.value)}
                className="w-full mt-1 p-2.5 bg-slate-950 border border-slate-800 rounded text-xs text-slate-200 focus:outline-none focus:border-slate-700 transition-colors placeholder-slate-700 resize-none font-mono"
              />
            </div>

            {/* Submit button */}
            <button
              type="submit"
              className="w-full py-2 bg-slate-800 hover:bg-slate-700 text-slate-100 border border-slate-700 font-bold rounded text-[10px] uppercase tracking-wider transition-all flex items-center justify-center gap-1.5 cursor-pointer font-mono"
            >
              <Plus className="w-4 h-4" />
              <span>Submit Ticket</span>
            </button>
          </form>
        </div>

        {/* Quick Diagnostic Tips */}
        <div className="bg-slate-900 border border-slate-800 p-3 rounded text-slate-400 text-[10px] leading-relaxed flex gap-2 border-l-2 border-emerald-500 select-none">
          <Sparkles className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
          <p>
            Completing repair tickets immediately updates the vehicle's telemetry profile. Any reported issue reduces system health scoring by 15%.
          </p>
        </div>
      </div>

    </div>
  );
}
