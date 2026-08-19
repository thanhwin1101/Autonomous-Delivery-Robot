export type AGVStatus = 'DELIVERING' | 'IDLE' | 'CHARGING' | 'STOPPED' | 'MAINTENANCE';

export type AGVType = 'Heavy-Duty' | 'Light-Delivery' | 'Forklift' | 'Tugger';

export interface Order {
  id: string;
  senderName: string;
  senderPhone: string;
  receiverName: string;
  receiverPhone: string;
  pickupTime: string;
  estimatedArrival: string;
  distanceRemaining: number; // in meters
  progress: 'pickup' | 'transit' | 'delivered';
}

export interface AGV {
  id: string;
  name: string;
  status: AGVStatus;
  battery: number;
  lat: number;
  lng: number;
  homeLat?: number;
  homeLng?: number;
  speed: number; // in m/s
  heading?: number; // yaw angle in degrees
  lastPing: number; // seconds ago
  type: AGVType;
  currentOrder?: Order;
  route: [number, number][];
  routeProgressIndex: number; // current step index in the route
  estimatedTimeRemaining: string;
  assignedStation: string;
  totalDistanceTraveled: number; // in km
  maintenanceScore: number; // percentage
}

export interface LogEntry {
  id: string;
  timestamp: string;
  agvId: string;
  type: 'info' | 'warning' | 'error' | 'success';
  message: string;
}

export interface MaintenanceRecord {
  id: string;
  agvId: string;
  issue: string;
  reportedAt: string;
  status: 'pending' | 'in_progress' | 'completed';
  technician: string;
  priority: 'low' | 'medium' | 'high';
}
