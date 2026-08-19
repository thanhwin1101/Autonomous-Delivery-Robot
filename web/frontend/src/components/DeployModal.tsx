import { useState, FormEvent } from 'react';
import { X, Cpu, Compass, Zap, MapPin } from 'lucide-react';
import { AGVType, AGV } from '../types';
import { ROUTE_AGV_01, ROUTE_AGV_02, ROUTE_AGV_03 } from '../data/mockData';

interface DeployModalProps {
  onClose: () => void;
  onDeploy: (newAgv: AGV) => void;
  existingCount: number;
}

export default function DeployModal({ onClose, onDeploy, existingCount }: DeployModalProps) {
  const defaultId = `AGV-${existingCount + 1 < 10 ? '0' : ''}${existingCount + 1}`;
  const [id, setId] = useState(defaultId);
  const [name, setName] = useState('');
  const [classification, setClassification] = useState<AGVType>('Light-Delivery');
  const [battery, setBattery] = useState(100);
  const [assignedStation, setAssignedStation] = useState('Bay Alpha-1');
  const [selectedRoute, setSelectedRoute] = useState<'route1' | 'route2' | 'route3' | 'stationary'>('route1');

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!id.trim() || !name.trim()) return;

    // Map route choice to real coordinate list
    let routeCoords: [number, number][] = [];
    let initialLat = 48.1351;
    let initialLng = 11.5820;

    if (selectedRoute === 'route1') {
      routeCoords = ROUTE_AGV_01;
      initialLat = ROUTE_AGV_01[0][0];
      initialLng = ROUTE_AGV_01[0][1];
    } else if (selectedRoute === 'route2') {
      routeCoords = ROUTE_AGV_02;
      initialLat = ROUTE_AGV_02[0][0];
      initialLng = ROUTE_AGV_02[0][1];
    } else if (selectedRoute === 'route3') {
      routeCoords = ROUTE_AGV_03;
      initialLat = ROUTE_AGV_03[0][0];
      initialLng = ROUTE_AGV_03[0][1];
    } else {
      // stationary
      initialLat = 48.1351 + (Math.random() - 0.5) * 0.01;
      initialLng = 11.5820 + (Math.random() - 0.5) * 0.01;
    }

    const newAgv: AGV = {
      id: id.trim().toUpperCase(),
      name: name.trim(),
      status: selectedRoute === 'stationary' ? 'IDLE' : 'DELIVERING',
      battery,
      lat: initialLat,
      lng: initialLng,
      speed: selectedRoute === 'stationary' ? 0 : 1.2,
      lastPing: 1,
      type: classification,
      assignedStation,
      totalDistanceTraveled: 0,
      maintenanceScore: 100,
      estimatedTimeRemaining: selectedRoute === 'stationary' ? 'N/A' : '30m',
      routeProgressIndex: 0,
      route: routeCoords,
    };

    onDeploy(newAgv);
  };

  return (
    <div className="fixed inset-0 bg-slate-950/80 z-50 p-4 flex items-center justify-center">
      <div className="bg-slate-900 w-full max-w-md rounded-md border border-slate-800 shadow-2xl p-5 relative text-slate-200">
        
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-1 text-slate-400 hover:text-slate-200 hover:bg-slate-800 rounded transition-colors cursor-pointer"
        >
          <X className="w-4 h-4" />
        </button>

        {/* Modal Header */}
        <div className="flex items-center gap-2.5 mb-4 select-none border-b border-slate-800 pb-3">
          <div className="w-7 h-7 rounded bg-slate-800 flex items-center justify-center text-slate-300">
            <Cpu className="w-3.5 h-3.5" />
          </div>
          <div>
            <h2 className="text-sm font-bold uppercase tracking-wider text-slate-200 font-mono">Deploy New AGV Unit</h2>
            <p className="text-[10px] text-slate-500 mt-0.5">Add an autonomous transit vehicle to the live warehouse floor grid.</p>
          </div>
        </div>

        {/* Input Fields Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Row: ID & Nickname */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Vehicle ID</label>
              <input
                type="text"
                required
                maxLength={8}
                value={id}
                onChange={(e) => setId(e.target.value)}
                placeholder="e.g. AGV-15"
                className="w-full mt-1 px-3 py-1.5 text-xs bg-slate-950 border border-slate-800 rounded text-slate-200 focus:outline-none focus:border-slate-700 font-mono"
              />
            </div>
            <div className="space-y-1">
              <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Nickname</label>
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Frostbite"
                className="w-full mt-1 px-3 py-1.5 text-xs bg-slate-950 border border-slate-800 rounded text-slate-200 focus:outline-none focus:border-slate-700 font-mono"
              />
            </div>
          </div>

          {/* Row: Classification & Station */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Classification</label>
              <select
                value={classification}
                onChange={(e) => setClassification(e.target.value as AGVType)}
                className="w-full mt-1 px-3 py-1.5 text-xs bg-slate-950 border border-slate-800 rounded text-slate-200 focus:outline-none focus:border-slate-700 font-mono"
              >
                <option value="Light-Delivery">Light-Delivery</option>
                <option value="Heavy-Duty">Heavy-Duty</option>
                <option value="Forklift">Forklift</option>
                <option value="Tugger">Tugger</option>
              </select>
            </div>
            <div className="space-y-1">
              <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Station Bay</label>
              <input
                type="text"
                required
                value={assignedStation}
                onChange={(e) => setAssignedStation(e.target.value)}
                placeholder="Bay Alpha-1"
                className="w-full mt-1 px-3 py-1.5 text-xs bg-slate-950 border border-slate-800 rounded text-slate-200 focus:outline-none focus:border-slate-700 font-mono"
              />
            </div>
          </div>

          {/* Initial Battery Charge Slider */}
          <div className="space-y-1">
            <div className="flex justify-between text-[9px] font-bold uppercase tracking-wider font-mono">
              <span className="text-slate-400">Battery Level</span>
              <span className="text-emerald-400">{battery}%</span>
            </div>
            <div className="flex items-center gap-3">
              <Zap className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              <input
                type="range"
                min="20"
                max="100"
                value={battery}
                onChange={(e) => setBattery(Number(e.target.value))}
                className="w-full h-1 bg-slate-950 rounded appearance-none cursor-pointer accent-slate-300"
              />
            </div>
          </div>

          {/* Path / Route selection */}
          <div className="space-y-1">
            <label className="block text-[9px] font-bold text-slate-400 uppercase tracking-wider font-mono">Transit Route Path</label>
            <div className="grid grid-cols-2 gap-2 mt-1">
              {[
                { id: 'route1', label: 'Altstadt Alpha', icon: Compass },
                { id: 'route2', label: 'Altstadt Beta', icon: Compass },
                { id: 'route3', label: 'Altstadt Gamma', icon: Compass },
                { id: 'stationary', label: 'Stationary Hold', icon: MapPin },
              ].map((route) => {
                const Icon = route.icon;
                const isSelected = selectedRoute === route.id;
                return (
                  <button
                    key={route.id}
                    type="button"
                    onClick={() => setSelectedRoute(route.id as any)}
                    className={`p-2 rounded border text-left cursor-pointer transition-all flex flex-col justify-between h-14 ${
                      isSelected
                        ? 'bg-slate-800 border-slate-700 text-slate-200'
                        : 'bg-slate-950 border-slate-800 text-slate-500 hover:text-slate-300'
                    }`}
                  >
                    <Icon className="w-3.5 h-3.5 shrink-0" />
                    <span className="text-[9px] font-bold uppercase tracking-wider font-mono leading-none">{route.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Submit Actions */}
          <div className="pt-3 border-t border-slate-800 flex gap-2 justify-end">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 bg-slate-950 border border-slate-800 text-slate-400 hover:text-slate-200 text-[10px] font-bold uppercase tracking-wider rounded cursor-pointer transition-all font-mono"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-4 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-950 text-[10px] font-bold uppercase tracking-wider rounded cursor-pointer transition-all font-mono"
            >
              Deploy Unit
            </button>
          </div>

        </form>
      </div>
    </div>
  );
}
