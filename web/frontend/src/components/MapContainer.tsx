import { useEffect, useRef, MutableRefObject } from 'react';
import L from 'leaflet';
import { AGV } from '../types';

interface MapContainerProps {
  agvs: AGV[];
  selectedAgvId: string | null;
  setSelectedAgvId: (id: string) => void;
  trafficHeatmapActive: boolean;
  mapRef: MutableRefObject<L.Map | null>;
  onSendCommand?: (cmd: any) => void;
  selectedPendingOrder?: any;
}

export default function MapContainer({
  agvs,
  selectedAgvId,
  setSelectedAgvId,
  trafficHeatmapActive,
  mapRef,
  onSendCommand,
  selectedPendingOrder,
}: MapContainerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const markersRef = useRef<{ [key: string]: L.Marker }>({});
  const markerARef = useRef<{ [key: string]: L.Marker }>({});
  const markerBRef = useRef<{ [key: string]: L.Marker }>({});
  const homeMarkerRef = useRef<{ [key: string]: L.Marker }>({});
  const routeLinesRef = useRef<{ [key: string]: { traversed: L.Polyline, remaining: L.Polyline } }>({});
  const pendingRouteRef = useRef<L.Polyline | null>(null);
  const pendingMarkerARef = useRef<L.Marker | null>(null);
  const pendingMarkerBRef = useRef<L.Marker | null>(null);
  const osrmCacheRef = useRef<{ [key: string]: { start: string, end: string, coords: [number, number][] } }>({});
  const heatmapLayerRef = useRef<L.LayerGroup | null>(null);
  const hasInitialPannedRef = useRef<boolean>(false);
  const agvVisualsRef = useRef<{ [key: string]: { color: string; statusClass: string; heading: number } }>({});

  // Initialize Map
  useEffect(() => {
    if (!containerRef.current) return;

    // Create Leaflet map centered on Munich
    const map = L.map(containerRef.current, {
      zoomControl: false,
      attributionControl: false,
    }).setView([48.1351, 11.5820], 15);

    mapRef.current = map;

    // Maptiler Light Theme using the API key from pi_master
    L.tileLayer('https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=UlkswUnpPl4Fjwa9uQO3', {
      maxZoom: 19,
      attribution: '\u003ca href="https://www.maptiler.com/copyright/" target="_blank"\u003e\u0026copy; MapTiler\u003c/a\u003e \u003ca href="https://www.openstreetmap.org/copyright" target="_blank"\u003e\u0026copy; OpenStreetMap contributors\u003c/a\u003e'
    }).addTo(map);

    // Create a group for heatmap overlays
    heatmapLayerRef.current = L.layerGroup().addTo(map);

    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, [mapRef]);

  // Handle Set Home (Context Menu / Long Press)
  const latestPropsRef = useRef({ onSendCommand, selectedAgvId, agvs });
  useEffect(() => {
    latestPropsRef.current = { onSendCommand, selectedAgvId, agvs };
  }, [onSendCommand, selectedAgvId, agvs]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const handleContextMenu = (e: L.LeafletMouseEvent) => {
      const lat = e.latlng.lat;
      const lon = e.latlng.lng;
      if (window.confirm(`Set AGV Home Location to ${lat.toFixed(6)}, ${lon.toFixed(6)}?`)) {
        const { onSendCommand, selectedAgvId, agvs } = latestPropsRef.current;
        if (onSendCommand && agvs.length > 0) {
          const targetId = selectedAgvId || agvs[0].id;
          onSendCommand({
            agvId: targetId,
            action: 'set_home',
            lat: lat,
            lon: lon
          });
          
          // Visual feedback
          const homeIcon = L.divIcon({
            html: `<div class="bg-blue-500 w-4 h-4 rounded-full border-2 border-white shadow-[0_0_10px_rgba(59,130,246,0.8)] animate-pulse"></div>`,
            className: ''
          });
          const marker = L.marker([lat, lon], { icon: homeIcon }).addTo(map)
            .bindPopup("<strong class='text-blue-600'>New Home Set</strong><br/>Will persist across reboots.")
            .openPopup();
            
          setTimeout(() => {
            if (mapRef.current) marker.remove();
          }, 5000);
        } else {
          alert('No AGV active to receive command.');
        }
      }
    };

    map.on('contextmenu', handleContextMenu);
    return () => {
      map.off('contextmenu', handleContextMenu);
    };
  }, [mapRef.current]); // Only re-bind if map instance changes

  // Handle Heatmap Toggle
  useEffect(() => {
    const map = mapRef.current;
    const heatmapGroup = heatmapLayerRef.current;
    if (!map || !heatmapGroup) return;

    heatmapGroup.clearLayers();

    if (trafficHeatmapActive) {
      // Simulate heavy warehouse traffic zones / congestion centers in Munich Altstadt
      const trafficZones: { center: [number, number]; radius: number; color: string; opacity: number }[] = [
        { center: [48.1345, 11.5790], radius: 180, color: '#f87171', opacity: 0.18 }, // High traffic (Red)
        { center: [48.1380, 11.5840], radius: 220, color: '#fb923c', opacity: 0.15 }, // Moderate traffic (Orange)
        { center: [48.1315, 11.5750], radius: 150, color: '#f87171', opacity: 0.15 }, // High traffic (Red)
        { center: [48.1400, 11.5800], radius: 120, color: '#fb923c', opacity: 0.12 }, // Moderate (Orange)
      ];

      trafficZones.forEach((zone) => {
        L.circle(zone.center, {
          radius: zone.radius,
          fillColor: zone.color,
          fillOpacity: zone.opacity,
          color: zone.color,
          weight: 1.5,
          opacity: zone.opacity * 2,
        }).addTo(heatmapGroup);
      });
    }
  }, [trafficHeatmapActive, mapRef]);

  // Synchronize Markers and Routes
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    // Track which AGVs are still active
    const activeAgvIds = new Set(agvs.map(a => a.id));

    // Remove markers/lines for AGVs that no longer exist
    Object.keys(markersRef.current).forEach((id) => {
      if (!activeAgvIds.has(id)) {
        markersRef.current[id].remove();
        delete markersRef.current[id];
      }
    });
    Object.keys(routeLinesRef.current).forEach((id) => {
      if (!activeAgvIds.has(id)) {
        routeLinesRef.current[id].traversed.remove();
        routeLinesRef.current[id].remaining.remove();
        delete routeLinesRef.current[id];
      }
    });
    Object.keys(markerARef.current).forEach((id) => {
      if (!activeAgvIds.has(id)) {
        markerARef.current[id].remove();
        delete markerARef.current[id];
      }
    });
    Object.keys(markerBRef.current).forEach((id) => {
      if (!activeAgvIds.has(id)) {
        markerBRef.current[id].remove();
        delete markerBRef.current[id];
      }
    });
    Object.keys(homeMarkerRef.current).forEach((id) => {
      if (!activeAgvIds.has(id)) {
        homeMarkerRef.current[id].remove();
        delete homeMarkerRef.current[id];
      }
    });

    agvs.forEach((agv) => {
      const isSelected = selectedAgvId === agv.id;
      const isMoving = agv.status === 'DELIVERING';
      const color = isSelected ? '#7dd3fc' : isMoving ? '#34d399' : agv.status === 'STOPPED' ? '#ef4444' : '#a0b4c4';
      const statusClass = agv.status === 'STOPPED' ? '' : 'pulse-glow';

      const heading = agv.heading || 0;
      const arrowOpacity = agv.heading !== undefined ? 1 : 0;

      // Custom icon using raw inline style to ensure exact coloring matches state
      const customIcon = L.divIcon({
        html: `
          <div style="position: relative; display: flex; align-items: center; justify-content: center; width: 32px; height: 32px;">
            <div class="${statusClass}" style="position: absolute; width: 24px; height: 24px; border-radius: 9999px; background-color: ${color}; opacity: 0.25;"></div>
            <svg style="position: absolute; width: 32px; height: 32px; transform: rotate(${heading}deg); opacity: ${arrowOpacity}; z-index: 1; filter: drop-shadow(0px 1px 2px rgba(0,0,0,0.3));" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L19 21L12 17L5 21L12 2Z" fill="${color}" stroke="#ffffff" stroke-width="1.5" stroke-linejoin="round"/>
            </svg>
            <div style="position: absolute; z-index: 2; width: 10px; height: 10px; border-radius: 9999px; background-color: ${color}; border: 1.5px solid #ffffff; box-shadow: 0 0 10px ${color};"></div>
          </div>
        `,
        className: '',
        iconSize: [32, 32],
        iconAnchor: [16, 16],
      });

      // Prepare Popup Content
      let popupHtml = '';
      if (isMoving && agv.currentOrder) {
        popupHtml = `
          <div style="font-family: 'Inter', sans-serif; padding: 4px; min-width: 200px;">
            <h4 style="margin: 0 0 8px 0; font-size: 13px; font-weight: bold; color: #1e293b; border-bottom: 1px solid #e2e8f0; padding-bottom: 4px;">🚚 Delivery #${agv.currentOrder.id}</h4>
            <div style="margin-bottom: 8px;">
              <p style="margin: 0; font-size: 10px; color: #64748b; text-transform: uppercase; font-weight: bold;">Sender</p>
              <p style="margin: 2px 0 0 0; font-size: 12px; font-weight: 600; color: #0f172a;">${agv.currentOrder.senderName}</p>
              <p style="margin: 2px 0 0 0; font-size: 11px; color: #475569;">${agv.currentOrder.senderPhone}</p>
            </div>
            <div>
              <p style="margin: 0; font-size: 10px; color: #64748b; text-transform: uppercase; font-weight: bold;">Receiver</p>
              <p style="margin: 2px 0 0 0; font-size: 12px; font-weight: 600; color: #0f172a;">${agv.currentOrder.receiverName}</p>
              <p style="margin: 2px 0 0 0; font-size: 11px; color: #475569;">${agv.currentOrder.receiverPhone}</p>
            </div>
          </div>
        `;
      } else {
        popupHtml = `
          <div style="font-family: 'Inter', sans-serif; padding: 4px; min-width: 160px;">
            <h4 style="margin: 0 0 8px 0; font-size: 13px; font-weight: bold; color: #1e293b; border-bottom: 1px solid #e2e8f0; padding-bottom: 4px;">🤖 ${agv.name}</h4>
            <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
              <span style="font-size: 11px; color: #64748b; font-weight: 600;">Status:</span>
              <span style="font-size: 11px; font-weight: bold; color: ${color};">${agv.status}</span>
            </div>
            <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
              <span style="font-size: 11px; color: #64748b; font-weight: 600;">Battery:</span>
              <span style="font-size: 11px; font-weight: bold; color: ${agv.battery > 20 ? '#22c55e' : '#ef4444'};">${agv.battery}%</span>
            </div>
             <div style="display: flex; justify-content: space-between;">
              <span style="font-size: 11px; color: #64748b; font-weight: 600;">Speed:</span>
              <span style="font-size: 11px; font-weight: bold; color: #0f172a;">${(agv.speed * 3.6).toFixed(1)} km/h</span>
            </div>
          </div>
        `;
      }

      // Check if marker already exists
      let marker = markersRef.current[agv.id];
      const prevVisuals = agvVisualsRef.current[agv.id];
      const visualsChanged = !prevVisuals || prevVisuals.color !== color || prevVisuals.statusClass !== statusClass || prevVisuals.heading !== heading;

      if (!marker) {
        // Create new marker
        marker = L.marker([agv.lat, agv.lng], { icon: customIcon }).addTo(map);
        markersRef.current[agv.id] = marker;
        agvVisualsRef.current[agv.id] = { color, statusClass, heading };

        marker.on('click', () => {
          setSelectedAgvId(agv.id);
        });

        marker.bindPopup(popupHtml, {
          className: 'custom-leaflet-popup',
          offset: [0, -10]
        });
      } else {
        // Update existing marker position
        marker.setLatLng([agv.lat, agv.lng]);
        
        // Only update icon if state/color changed to prevent animation reset
        if (visualsChanged) {
          marker.setIcon(customIcon);
          agvVisualsRef.current[agv.id] = { color, statusClass, heading };
        }
        
        // Update popup content dynamically if it exists
        const popup = marker.getPopup();
        if (popup) {
          popup.setContent(popupHtml);
        }
      }

      // Always update tooltip to ensure color is fresh (removing and re-adding is fine for tooltip)
      marker.unbindTooltip();
      marker.bindTooltip(
        `<div style="font-family: 'Inter', sans-serif; font-size: 11px; font-weight: 700; color: ${color}; text-shadow: 0 1px 3px rgba(0,0,0,0.9);">${agv.id}</div>`,
        {
          permanent: true,
          direction: 'top',
          className: 'robot-marker-label',
          offset: [0, -4],
        }
      );

      // Draw/Update Routes
      const shouldShowRoute = agv.route && agv.route.length === 2 && agv.status !== 'IDLE' && agv.status !== 'OFFLINE';
      if (shouldShowRoute) {
        const start = agv.route[0];
        const end = agv.route[1];
        const startStr = `${start[0].toFixed(5)},${start[1].toFixed(5)}`;
        const endStr = `${end[0].toFixed(5)},${end[1].toFixed(5)}`;

        // Fetch OSRM if not cached
        const cache = osrmCacheRef.current[agv.id];
        if (!cache || cache.start !== startStr || cache.end !== endStr) {
          // Prepare waypoints. If AGV has valid GPS, route from AGV -> Sender -> Receiver
          let waypoints = `${start[1]},${start[0]};${end[1]},${end[0]}`;
          if (agv.lat && agv.lng && agv.lat !== 0 && agv.lng !== 0) {
            waypoints = `${agv.lng},${agv.lat};${waypoints}`;
          }
          
          fetch(`https://router.project-osrm.org/route/v1/driving/${waypoints}?overview=full&geometries=geojson`)
            .then(res => res.json())
            .then(data => {
              if (data.routes && data.routes.length > 0) {
                // Convert [lng, lat] to [lat, lng]
                const coords: [number, number][] = data.routes[0].geometry.coordinates.map((c: any) => [c[1], c[0]]);
                osrmCacheRef.current[agv.id] = { start: startStr, end: endStr, coords };
                // Re-trigger update
                updateRouteLines(agv, coords, map);
              }
            })
            .catch(err => console.error("OSRM fetch error", err));
        } else {
          updateRouteLines(agv, cache.coords, map);
        }

        // Draw Markers A & B
        if (!markerARef.current[agv.id]) {
          markerARef.current[agv.id] = L.marker([start[0], start[1]], {
            icon: L.divIcon({ html: `<div style="width:16px;height:16px;background:#3b82f6;border-radius:50%;border:2px solid white;"></div>`, className: '' })
          }).addTo(map);
        } else {
          markerARef.current[agv.id].setLatLng([start[0], start[1]]);
        }

        if (!markerBRef.current[agv.id]) {
          markerBRef.current[agv.id] = L.marker([end[0], end[1]], {
            icon: L.divIcon({ html: `<div style="width:16px;height:16px;background:#ef4444;border-radius:50%;border:2px solid white;"></div>`, className: '' })
          }).addTo(map);
        } else {
          markerBRef.current[agv.id].setLatLng([end[0], end[1]]);
        }
      } else {
        // Clear routes and A/B markers
        if (routeLinesRef.current[agv.id]) {
          routeLinesRef.current[agv.id].traversed.remove();
          routeLinesRef.current[agv.id].remaining.remove();
          delete routeLinesRef.current[agv.id];
        }
        if (markerARef.current[agv.id]) {
          markerARef.current[agv.id].remove();
          delete markerARef.current[agv.id];
        }
        if (markerBRef.current[agv.id]) {
          markerBRef.current[agv.id].remove();
          delete markerBRef.current[agv.id];
        }
        if (map && !hasInitialPannedRef.current) {
          hasInitialPannedRef.current = true;
          map.setView([agv.lat, agv.lng], 15);
        }
      }

      // Draw Home Marker if available
      if (agv.homeLat !== undefined && agv.homeLng !== undefined) {
        let hMarker = homeMarkerRef.current[agv.id];
        const latlng: L.LatLngTuple = [agv.homeLat, agv.homeLng];
        
        if (!hMarker) {
          const homeIcon = L.divIcon({
            html: `<div class="bg-blue-500 w-5 h-5 rounded-full border-2 border-white flex items-center justify-center shadow-md"><span class="material-symbols-outlined text-white" style="font-size: 14px;">home</span></div>`,
            className: ''
          });
          hMarker = L.marker(latlng, { icon: homeIcon }).addTo(map)
            .bindPopup("<strong class='text-blue-600'>AGV Home</strong>");
          homeMarkerRef.current[agv.id] = hMarker;
        } else {
          hMarker.setLatLng(latlng);
        }
      }
    });
  }, [agvs, selectedAgvId, mapRef, setSelectedAgvId]);

  // Handle Pending Order Route Visualization
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    // Clear existing pending route
    if (pendingRouteRef.current) {
      pendingRouteRef.current.remove();
      pendingRouteRef.current = null;
    }
    if (pendingMarkerARef.current) {
      pendingMarkerARef.current.remove();
      pendingMarkerARef.current = null;
    }
    if (pendingMarkerBRef.current) {
      pendingMarkerBRef.current.remove();
      pendingMarkerBRef.current = null;
    }

    if (selectedPendingOrder) {
      const start = [
        Number(selectedPendingOrder.pickupLat || selectedPendingOrder.senderLat || selectedPendingOrder.sender_lat),
        Number(selectedPendingOrder.pickupLng || selectedPendingOrder.senderLon || selectedPendingOrder.sender_lon || selectedPendingOrder.pickupLon)
      ];
      const end = [
        Number(selectedPendingOrder.dropoffLat || selectedPendingOrder.receiverLat || selectedPendingOrder.recv_lat),
        Number(selectedPendingOrder.dropoffLng || selectedPendingOrder.receiverLon || selectedPendingOrder.recv_lon || selectedPendingOrder.dropoffLon)
      ];

      if (start[0] && start[1] && end[0] && end[1]) {
        // Draw Markers
        pendingMarkerARef.current = L.marker([start[0], start[1]], {
          icon: L.divIcon({ 
            html: `<div style="display:flex;flex-direction:column;align-items:center;transform:translateY(-10px);">
                     <div style="background:#f59e0b;color:white;font-size:10px;font-weight:bold;padding:2px 6px;border-radius:4px;white-space:nowrap;margin-bottom:2px;box-shadow:0 2px 4px rgba(0,0,0,0.3);">Sender</div>
                     <div style="width:16px;height:16px;background:#f59e0b;border-radius:50%;border:2px solid white;box-shadow:0 2px 4px rgba(0,0,0,0.3);"></div>
                   </div>`, 
            className: '' 
          })
        }).addTo(map);

        pendingMarkerBRef.current = L.marker([end[0], end[1]], {
          icon: L.divIcon({ 
            html: `<div style="display:flex;flex-direction:column;align-items:center;transform:translateY(-10px);">
                     <div style="background:#ef4444;color:white;font-size:10px;font-weight:bold;padding:2px 6px;border-radius:4px;white-space:nowrap;margin-bottom:2px;box-shadow:0 2px 4px rgba(0,0,0,0.3);">Receiver</div>
                     <div style="width:16px;height:16px;background:#ef4444;border-radius:50%;border:2px solid white;box-shadow:0 2px 4px rgba(0,0,0,0.3);"></div>
                   </div>`, 
            className: '' 
          })
        }).addTo(map);

        // Fetch Route
        const waypoints = `${start[1]},${start[0]};${end[1]},${end[0]}`;
        fetch(`https://router.project-osrm.org/route/v1/driving/${waypoints}?overview=full&geometries=geojson`)
          .then(res => res.json())
          .then(data => {
            if (data.routes && data.routes[0]) {
              const routeInfo = data.routes[0];
              const coords = routeInfo.geometry.coordinates.map((c: any) => [c[1], c[0]] as [number, number]);
              
              const distanceKm = (routeInfo.distance / 1000).toFixed(1);
              
              pendingRouteRef.current = L.polyline(coords, {
                color: '#f59e0b',
                weight: 4,
                opacity: 0.8,
                dashArray: '10, 10'
              }).addTo(map);
              
              // Bind popup with distance and open it at the middle of the route
              const midPoint = coords[Math.floor(coords.length / 2)];
              const popup = L.popup({ closeButton: false, autoClose: false, className: 'custom-popup' })
                .setLatLng(midPoint)
                .setContent(`<div class="font-bold text-slate-800 text-sm text-center">Distance<br/><span class="text-orange-500 text-lg">${distanceKm} km</span></div>`);
              
              pendingRouteRef.current.bindPopup(popup).openPopup();

              // Fit bounds to show the entire route with a slight delay to account for UI transitions
              const bounds = L.latLngBounds(coords);
              setTimeout(() => {
                if (mapRef.current) {
                  mapRef.current.fitBounds(bounds, { padding: [50, 50], animate: true, duration: 1 });
                }
              }, 300); // 300ms delay matches tailwind transition-all duration-300
            }
          })
          .catch(err => console.error("OSRM fetch error for pending order", err));
      }
    }
  }, [selectedPendingOrder, mapRef]);

  // Helper function to update split lines
  const updateRouteLines = (agv: AGV, coords: [number, number][], map: L.Map) => {
    // Find closest point to split
    let closestIdx = 0;
    let minDistance = Infinity;
    coords.forEach((coord, i) => {
      const dist = Math.hypot(coord[0] - agv.lat, coord[1] - agv.lng);
      if (dist < minDistance) {
        minDistance = dist;
        closestIdx = i;
      }
    });

    const traversed = coords.slice(0, closestIdx + 1);
    const remaining = coords.slice(closestIdx);
    
    // Smooth the transition by adding the exact AGV pos to the split point
    traversed.push([agv.lat, agv.lng]);
    remaining.unshift([agv.lat, agv.lng]);

    const isCurrentSelected = selectedAgvId === agv.id;
    let lines = routeLinesRef.current[agv.id];

    if (!lines) {
      lines = {
        traversed: L.polyline(traversed, {
          color: '#9ca3af', // Gray
          weight: 4,
          opacity: 0.8,
        }).addTo(map),
        remaining: L.polyline(remaining, {
          color: '#10b981', // Green
          weight: 4,
          opacity: 0.8,
        }).addTo(map)
      };
      routeLinesRef.current[agv.id] = lines;
    } else {
      lines.traversed.setLatLngs(traversed);
      lines.remaining.setLatLngs(remaining);
      lines.traversed.setStyle({ weight: isCurrentSelected ? 6 : 4 });
      lines.remaining.setStyle({ weight: isCurrentSelected ? 6 : 4 });
    }
  };

  // Auto-follow selected AGV only on first valid GPS fix
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !selectedAgvId) return;

    const targetAgv = agvs.find((a) => a.id === selectedAgvId);
    // Only pan if we have valid coordinates (not 0,0) and haven't panned yet
    if (targetAgv && targetAgv.lat !== 0 && targetAgv.lng !== 0 && !hasInitialPannedRef.current) {
      map.setView([targetAgv.lat, targetAgv.lng], 18, { animate: true, duration: 0.5 });
      hasInitialPannedRef.current = true;
    }
  }, [selectedAgvId, agvs, mapRef]);

  return (
    <div className="w-full h-full relative">
      {/* Leaflet Mount Point */}
      <div ref={containerRef} className="w-full h-full" />
    </div>
  );
}
