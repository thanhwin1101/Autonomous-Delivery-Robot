import React, { useState } from 'react';
import { Play, Square, Settings, Bluetooth, Send, Activity, MonitorSmartphone, MapPin, Terminal } from 'lucide-react';
import { AGV, LogEntry } from '../types';

interface TestingViewProps {
  agvs: AGV[];
  selectedAgvId: string | null;
  onSendCommand: (command: any) => void;
  isSidebarCollapsed: boolean;
  logs: LogEntry[];
}

const AGV_STATES = [
  'INIT', 'IDLE', 'GOING_TO_SENDER', 'WAITING_SENDER', 
  'ROUTING_TO_RECEIVER', 'GOING_TO_RECEIVER', 'WAITING_RECEIVER', 
  'ROUTING_TO_HOME', 'RETURNING'
];

export default function TestingView({ agvs, selectedAgvId, onSendCommand, isSidebarCollapsed, logs }: TestingViewProps) {
  const [selectedState, setSelectedState] = useState('IDLE');
  const [bleToken, setBleToken] = useState('');
  const [overrideLat, setOverrideLat] = useState('48.1351');
  const [overrideLon, setOverrideLon] = useState('11.5820');
  const [testAngle, setTestAngle] = useState('90');

  const selectedAgv = agvs.find(a => a.id === selectedAgvId);

  const handleForceState = () => {
    if (!selectedAgvId) return alert('Please select an AGV to send commands!');
    onSendCommand({
      agvId: selectedAgvId,
      action: 'force_state',
      state: selectedState
    });
  };

  const handleBleOn = () => {
    if (!selectedAgvId) return alert('Please select an AGV to send commands!');
    const tokenToSend = bleToken.trim() || 'TEST_TOKEN_123';
    onSendCommand({
      agvId: selectedAgvId,
      action: 'ble_on',
      token: tokenToSend
    });
  };

  const handleBleOff = () => {
    if (!selectedAgvId) return alert('Please select an AGV to send commands!');
    onSendCommand({
      agvId: selectedAgvId,
      action: 'ble_off'
    });
  };

  const handleEnableGpsOverride = () => {
    if (!selectedAgvId) return alert('Please select an AGV to send commands!');
    onSendCommand({
      agvId: selectedAgvId,
      action: 'override_gps',
      disable: false,
      lat: parseFloat(overrideLat.replace(',', '.')),
      lon: parseFloat(overrideLon.replace(',', '.'))
    });
  };

  const handleDisableGpsOverride = () => {
    if (!selectedAgvId) return alert('Please select an AGV to send commands!');
    onSendCommand({
      agvId: selectedAgvId,
      action: 'override_gps',
      disable: true
    });
  };

  const handleTestRotation = () => {
    if (!selectedAgvId) return alert('Please select an AGV to send commands!');
    onSendCommand({
      agvId: selectedAgvId,
      action: 'test_rotate',
      angle: parseFloat(testAngle)
    });
  };

  const handleCancelRotation = () => {
    if (!selectedAgvId) return alert('Please select an AGV to send commands!');
    onSendCommand({
      agvId: selectedAgvId,
      action: 'cancel_test_rotate'
    });
  };

  return (
    <div className={`absolute inset-y-0 right-0 top-12 bottom-0 overflow-y-auto p-6 bg-slate-950/90 backdrop-blur-md z-30 flex flex-col space-y-6 text-slate-200 transition-all duration-300 ${isSidebarCollapsed ? 'left-20' : 'left-64'}`}>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-100 flex items-center gap-2">
            <Settings className="w-6 h-6 text-emerald-400" />
            AGV Testing & Control Panel
          </h1>
          <p className="text-slate-400 mt-1">State Machine and hardware testing interface</p>
        </div>
      </div>

      {!selectedAgvId ? (
        <div className="bg-slate-800/50 border border-slate-700 rounded-xl p-8 text-center text-slate-400">
          Please wait for an AGV to connect or select one to test.
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          
          {/* STATE MACHINE CARD */}
          <div className="bg-slate-800 border border-slate-700 rounded-xl p-6">
            <h2 className="text-lg font-bold text-slate-200 flex items-center gap-2 mb-4">
              <Activity className="w-5 h-5 text-blue-400" />
              State Machine Override
            </h2>
            
            <div className="mb-4 bg-slate-900/50 p-4 rounded-lg border border-slate-700">
              <p className="text-sm text-slate-400 mb-1">Current state of {selectedAgvId}:</p>
              <p className="text-xl font-mono font-bold text-emerald-400">{selectedAgv?.status || 'UNKNOWN'}</p>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-400 mb-2">Select state to enforce (Force State):</label>
                <select 
                  value={selectedState}
                  onChange={(e) => setSelectedState(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-600 text-slate-200 rounded-md px-4 py-2 focus:outline-none focus:border-emerald-500 transition-colors"
                >
                  {AGV_STATES.map(st => (
                    <option key={st} value={st}>{st}</option>
                  ))}
                </select>
              </div>

              <button 
                onClick={handleForceState}
                className="w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-500 text-white font-bold py-2 px-4 rounded-md transition-colors"
              >
                <Send className="w-4 h-4" />
                Send state transition command
              </button>
            </div>
          </div>

          {/* BLE TESTING CARD */}
          <div className="bg-slate-800 border border-slate-700 rounded-xl p-6">
            <h2 className="text-lg font-bold text-slate-200 flex items-center gap-2 mb-4">
              <Bluetooth className="w-5 h-5 text-indigo-400" />
              BLE Module Testing
            </h2>

            <div className="mb-4">
              <label className="block text-sm font-medium text-slate-400 mb-2">BLE Token (Optional):</label>
              <input 
                type="text" 
                value={bleToken}
                onChange={(e) => setBleToken(e.target.value)}
                placeholder="Enter Token (or leave blank for dummy token)"
                className="w-full bg-slate-900 border border-slate-600 text-slate-200 rounded-md px-4 py-2 focus:outline-none focus:border-emerald-500 transition-colors"
              />
              <p className="text-xs text-slate-500 mt-2">
                * In production, this token is fetched from Firebase and generated by the Android App. 
                For testing, you can enter it manually so the Android App can detect it.
              </p>
            </div>

            <div className="flex gap-4">
              <button 
                onClick={handleBleOn}
                className="flex-1 flex items-center justify-center gap-2 bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-2 px-4 rounded-md transition-colors"
              >
                <Play className="w-4 h-4" />
                Turn ON BLE (Simulate Unlock)
              </button>
              
              <button 
                onClick={handleBleOff}
                className="flex-1 flex items-center justify-center gap-2 bg-red-600 hover:bg-red-500 text-white font-bold py-2 px-4 rounded-md transition-colors"
              >
                <Square className="w-4 h-4" />
                Turn OFF BLE (Simulate Lock)
              </button>
            </div>
          </div>
          
          {/* GPS OVERRIDE CARD */}
          <div className="bg-slate-800 border border-slate-700 rounded-xl p-6">
            <h2 className="text-lg font-bold text-slate-200 flex items-center gap-2 mb-4">
              <MapPin className="w-5 h-5 text-amber-400" />
              GPS Manual Override
            </h2>

            <div className="mb-4 bg-slate-900/50 p-4 rounded-lg border border-slate-700">
              <div className="flex justify-between items-center">
                <div>
                  <p className="text-sm text-slate-400 mb-1">Current coordinates of {selectedAgvId}:</p>
                  <p className="text-xl font-mono font-bold text-amber-400">
                    {selectedAgv ? `${selectedAgv.lat.toFixed(6)}, ${selectedAgv.lng.toFixed(6)}` : 'UNKNOWN'}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-sm text-slate-400 mb-1">State:</p>
                  <p className={`text-xl font-mono font-bold ${selectedAgv?.status === 'IDLE' ? 'text-slate-300' : 'text-emerald-400'}`}>
                    {selectedAgv?.status || 'UNKNOWN'}
                  </p>
                </div>
              </div>
            </div>

            <div className="space-y-4">
              <div className="flex gap-4">
                <div className="flex-1">
                  <label className="block text-sm font-medium text-slate-400 mb-2">Latitude:</label>
                  <input 
                    type="number" step="0.000001"
                    value={overrideLat}
                    onChange={(e) => setOverrideLat(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-600 text-slate-200 rounded-md px-4 py-2 focus:outline-none focus:border-amber-500 transition-colors"
                  />
                </div>
                <div className="flex-1">
                  <label className="block text-sm font-medium text-slate-400 mb-2">Longitude:</label>
                  <input 
                    type="number" step="0.000001"
                    value={overrideLon}
                    onChange={(e) => setOverrideLon(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-600 text-slate-200 rounded-md px-4 py-2 focus:outline-none focus:border-amber-500 transition-colors"
                  />
                </div>
              </div>

              <div className="flex gap-4 pt-2">
                <button 
                  onClick={handleEnableGpsOverride}
                  className="flex-1 flex items-center justify-center gap-2 bg-amber-600 hover:bg-amber-500 text-white font-bold py-2 px-4 rounded-md transition-colors"
                >
                  <Send className="w-4 h-4" />
                  Send Coordinates
                </button>
                <button 
                  onClick={handleDisableGpsOverride}
                  className="flex-1 flex items-center justify-center gap-2 bg-slate-600 hover:bg-slate-500 text-white font-bold py-2 px-4 rounded-md transition-colors"
                >
                  <Square className="w-4 h-4" />
                  Cancel Override
                </button>
              </div>
            </div>
          </div>
          
          {/* ROTATION TEST CARD */}
          <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 lg:col-span-2">
            <h2 className="text-lg font-bold text-slate-200 flex items-center gap-2 mb-4">
              <Activity className="w-5 h-5 text-fuchsia-400" />
              Rotation Test (IMU / Heading)
            </h2>
            <div className="flex gap-4 items-end">
              <div className="flex-1">
                <label className="block text-sm font-medium text-slate-400 mb-2">Target Angle (Degrees):</label>
                <input 
                  type="number" 
                  value={testAngle}
                  onChange={(e) => setTestAngle(e.target.value)}
                  placeholder="e.g. 90 for East"
                  className="w-full bg-slate-900 border border-slate-600 text-slate-200 rounded-md px-4 py-2 focus:outline-none focus:border-fuchsia-500 transition-colors"
                />
              </div>
              <button 
                onClick={handleTestRotation}
                className="flex-none flex items-center justify-center gap-2 bg-fuchsia-600 hover:bg-fuchsia-500 text-white font-bold py-2 px-6 rounded-md transition-colors"
              >
                <Play className="w-4 h-4" />
                Hold Angle
              </button>
              <button 
                onClick={handleCancelRotation}
                className="flex-none flex items-center justify-center gap-2 bg-slate-600 hover:bg-slate-500 text-white font-bold py-2 px-6 rounded-md transition-colors"
              >
                <Square className="w-4 h-4" />
                Stop
              </button>
            </div>
            <p className="text-xs text-slate-500 mt-3">
              This will force the AGV into TEST_ROTATION state. It will spin in place to the specified heading and actively hold that angle if pushed.
              (0 = North, 90 = East, 180 = South, -90/270 = West). Click Stop to return to IDLE.
            </p>
          </div>
          
          {/* TERMINAL LOGS CARD */}
          <div className="bg-slate-950 border border-slate-800 rounded-xl p-0 flex flex-col h-80 lg:col-span-2 overflow-hidden shadow-xl">
            <div className="bg-slate-900 border-b border-slate-800 px-4 py-3 flex items-center gap-2">
              <Terminal className="w-4 h-4 text-slate-400" />
              <h2 className="text-sm font-bold text-slate-300 font-mono tracking-wider uppercase">Live Debug Terminal</h2>
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-1 font-mono text-[11px] leading-relaxed">
              {logs.filter(l => l.agvId === selectedAgvId).slice(0, 50).map((log, i) => (
                <div key={log.id} className="flex gap-3 hover:bg-slate-900/50 px-1 rounded transition-colors break-words text-slate-400">
                  <span className="text-slate-500 whitespace-nowrap shrink-0">[{log.timestamp}]</span>
                  <span className={log.message.includes('[ESP32]') ? 'text-indigo-300' : 'text-slate-300'}>
                    {log.message}
                  </span>
                </div>
              ))}
              {logs.filter(l => l.agvId === selectedAgvId).length === 0 && (
                <div className="text-slate-600 text-center italic mt-10">No debug logs available for {selectedAgvId}.</div>
              )}
            </div>
          </div>

        </div>
      )}
    </div>
  );
}
