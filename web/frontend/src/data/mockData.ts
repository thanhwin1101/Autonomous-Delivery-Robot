import { AGV, LogEntry, MaintenanceRecord } from '../types';

// Let's define some beautiful coordinate paths around Munich Altstadt / Isarvorstadt
export const MUNICH_CENTER: [number, number] = [48.1351, 11.5820];

export const ROUTE_AGV_01: [number, number][] = [
  [48.1310, 11.5720],
  [48.1320, 11.5725],
  [48.1325, 11.5750],
  [48.1330, 11.5780],
  [48.1345, 11.5810],
  [48.1360, 11.5835]
];

export const ROUTE_AGV_02: [number, number][] = [
  [48.1395, 11.5890],
  [48.1380, 11.5870],
  [48.1365, 11.5855],
  [48.1355, 11.5830],
  [48.1351, 11.5820]
];

export const ROUTE_AGV_03: [number, number][] = [
  [48.1410, 11.5740],
  [48.1390, 11.5760],
  [48.1385, 11.5780],
  [48.1370, 11.5800],
  [48.1365, 11.5815]
];

export const INITIAL_AGVS: AGV[] = [
  {
    id: 'AGV-01',
    name: 'AGV-CongVinh01',
    status: 'DELIVERING',
    battery: 85,
    lat: 48.1360,
    lng: 11.5835,
    speed: 1.2,
    lastPing: 2,
    type: 'Light-Delivery',
    assignedStation: 'Bay Alpha-4',
    totalDistanceTraveled: 1248.5,
    maintenanceScore: 94,
    estimatedTimeRemaining: '25m',
    routeProgressIndex: 5,
    route: ROUTE_AGV_01,
    currentOrder: {
      id: '842',
      senderName: 'John Doe',
      senderPhone: '+1 234 567 890',
      receiverName: 'Jane Smith',
      receiverPhone: '+1 987 654 321',
      pickupTime: '14:20',
      estimatedArrival: '14:45',
      distanceRemaining: 320,
      progress: 'transit'
    }
  },
  {
    id: 'AGV-04',
    name: 'Glacier Hawk',
    status: 'IDLE',
    battery: 42,
    lat: 48.1385,
    lng: 11.5780,
    speed: 0.0,
    lastPing: 5,
    type: 'Heavy-Duty',
    assignedStation: 'Bay Beta-1',
    totalDistanceTraveled: 3840.2,
    maintenanceScore: 82,
    estimatedTimeRemaining: 'N/A',
    routeProgressIndex: 0,
    route: ROUTE_AGV_03,
  },
  {
    id: 'AGV-12',
    name: 'Frostbite',
    status: 'CHARGING',
    battery: 96,
    lat: 48.1420,
    lng: 11.5850,
    speed: 0.0,
    lastPing: 1,
    type: 'Tugger',
    assignedStation: 'Charging Station C',
    totalDistanceTraveled: 843.1,
    maintenanceScore: 98,
    estimatedTimeRemaining: '5m (to full)',
    routeProgressIndex: 0,
    route: [],
  },
  {
    id: 'AGV-09',
    name: 'Tundra Mule',
    status: 'MAINTENANCE',
    battery: 18,
    lat: 48.1325,
    lng: 11.5810,
    speed: 0.0,
    lastPing: 12,
    type: 'Forklift',
    assignedStation: 'Maintenance Workshop',
    totalDistanceTraveled: 5120.8,
    maintenanceScore: 45,
    estimatedTimeRemaining: 'N/A',
    routeProgressIndex: 0,
    route: [],
  },
  {
    id: 'AGV-05',
    name: 'SubZero',
    status: 'DELIVERING',
    battery: 74,
    lat: 48.1395,
    lng: 11.5890,
    speed: 1.5,
    lastPing: 3,
    type: 'Heavy-Duty',
    assignedStation: 'Bay Gamma-2',
    totalDistanceTraveled: 1950.4,
    maintenanceScore: 89,
    estimatedTimeRemaining: '12m',
    routeProgressIndex: 0,
    route: ROUTE_AGV_02,
    currentOrder: {
      id: '843',
      senderName: 'Alice Peterson',
      senderPhone: '+49 89 234567',
      receiverName: 'Robert Lang',
      receiverPhone: '+49 89 765432',
      pickupTime: '14:35',
      estimatedArrival: '14:55',
      distanceRemaining: 740,
      progress: 'pickup'
    }
  }
];

export const INITIAL_LOGS: LogEntry[] = [
  {
    id: 'log-1',
    timestamp: '14:48:12',
    agvId: 'AGV-01',
    type: 'info',
    message: 'AGV-01 entered transit phase towards Sector D'
  },
  {
    id: 'log-2',
    timestamp: '14:46:05',
    agvId: 'AGV-12',
    type: 'success',
    message: 'AGV-12 docked successfully at Charging Station C'
  },
  {
    id: 'log-3',
    timestamp: '14:45:00',
    agvId: 'AGV-09',
    type: 'warning',
    message: 'AGV-09 battery critical (18%). Maintenance dispatch initiated.'
  },
  {
    id: 'log-4',
    timestamp: '14:42:31',
    agvId: 'AGV-05',
    type: 'success',
    message: 'AGV-05 current order assigned: #843'
  },
  {
    id: 'log-5',
    timestamp: '14:30:15',
    agvId: 'AGV-04',
    type: 'info',
    message: 'AGV-04 status changed to IDLE at Bay Beta-1'
  }
];

export const INITIAL_MAINTENANCE: MaintenanceRecord[] = [
  {
    id: 'maint-1',
    agvId: 'AGV-09',
    issue: 'Optical navigation sensor misalignment / Battery degraded below threshold',
    reportedAt: '2026-07-02 11:24',
    status: 'in_progress',
    technician: 'Marcus V.',
    priority: 'high'
  },
  {
    id: 'maint-2',
    agvId: 'AGV-04',
    issue: 'Left rear drive motor slight vibration',
    reportedAt: '2026-07-01 16:45',
    status: 'pending',
    technician: 'Sarah K.',
    priority: 'medium'
  },
  {
    id: 'maint-3',
    agvId: 'AGV-12',
    issue: 'Scheduled tire wear replacement',
    reportedAt: '2026-07-02 08:00',
    status: 'completed',
    technician: 'Marcus V.',
    priority: 'low'
  }
];
