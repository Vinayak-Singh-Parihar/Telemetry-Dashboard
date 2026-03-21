import { useEffect, useState } from "react";
import { supabase } from "./supabase";

export default function App() {
  const [self, setSelf] = useState({ x: 5, y: 5 });
  const [selectedAnchor, setSelectedAnchor] = useState(null);

  const anchors = [
    { id: "A1", x: 2, y: 2 },
    { id: "A2", x: 8, y: 2 },
    { id: "A3", x: 5, y: 8 }
  ];

  // Distance
  const getDistance = (x1, y1, x2, y2) => {
    return Math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2);
  };

  // RSSI
  const getRSSI = (distance) => {
    const txPower = -59;
    const n = 2;
    return txPower - 10 * n * Math.log10(distance);
  };

  // REALTIME LISTENER
  useEffect(() => {
    const channel = supabase
      .channel("positions-channel")
      .on(
        "postgres_changes",
        {
          event: "*",
          schema: "public",
          table: "positions"
        },
        (payload) => {
          const data = payload.new;

          if (data.id === "self") {
            setSelf({
              x: data.x,
              y: data.y
            });
          }
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, []);

  return (
    <div
      style={{
        height: "100vh",
        background: "#0f172a",
        position: "relative",
        overflow: "hidden",
        color: "white",
        fontFamily: "sans-serif"
      }}
    >
      {/* GRID */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          backgroundImage: `
            linear-gradient(#1e293b 1px, transparent 1px),
            linear-gradient(90deg, #1e293b 1px, transparent 1px)
          `,
          backgroundSize: "50px 50px"
        }}
      />

      {/* LINES */}
      <svg
        style={{
          position: "absolute",
          inset: 0,
          width: "100%",
          height: "100%",
          pointerEvents: "none"
        }}
      >
        {anchors.map((a) => (
          <line
            key={a.id}
            x1={a.x * 50}
            y1={a.y * 50}
            x2={self.x * 50}
            y2={self.y * 50}
            stroke="cyan"
            strokeWidth="1"
            opacity="0.6"
          />
        ))}
      </svg>

      {/* ANCHORS */}
      {anchors.map((a) => {
        const distance = getDistance(a.x, a.y, self.x, self.y);
        const rssi = getRSSI(distance);

        return (
          <div
            key={a.id}
            onClick={() => setSelectedAnchor({ ...a, distance, rssi })}
            style={{
              position: "absolute",
              left: a.x * 50,
              top: a.y * 50,
              transform: "translate(-50%, -50%)",
              cursor: "pointer",
              zIndex: 2
            }}
          >
            <div
              style={{
                width: 16,
                height: 16,
                background:
                  selectedAnchor?.id === a.id ? "red" : "orange",
                borderRadius: "50%"
              }}
            />

            <div style={{ fontSize: "10px", textAlign: "center" }}>
              {a.id}
            </div>

            <div style={{ fontSize: "10px", color: "cyan" }}>
              {distance.toFixed(2)} m
            </div>

            <div style={{ fontSize: "10px", color: "lime" }}>
              {rssi.toFixed(0)} dBm
            </div>
          </div>
        );
      })}

      {/* YOU */}
      <div
        style={{
          position: "absolute",
          left: self.x * 50,
          top: self.y * 50,
          transform: "translate(-50%, -50%)",
          zIndex: 3
        }}
      >
        <div
          style={{
            width: 18,
            height: 18,
            background: "cyan",
            borderRadius: "50%",
            boxShadow: "0 0 12px cyan"
          }}
        />
      </div>

      {/* INFO PANEL */}
      {selectedAnchor && (
        <div
          style={{
            position: "absolute",
            right: 20,
            top: 20,
            background: "#1e293b",
            padding: "12px",
            borderRadius: "10px"
          }}
        >
          <div><b>Anchor:</b> {selectedAnchor.id}</div>
          <div><b>Distance:</b> {selectedAnchor.distance.toFixed(2)} m</div>
          <div><b>RSSI:</b> {selectedAnchor.rssi.toFixed(0)} dBm</div>
        </div>
      )}
    </div>
  );
}