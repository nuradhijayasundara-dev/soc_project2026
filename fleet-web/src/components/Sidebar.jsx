import React from "react";
import {
  Drawer, List, ListItemButton, ListItemIcon, ListItemText, Toolbar, Box, Typography,
} from "@mui/material";
import DashboardIcon from "@mui/icons-material/Dashboard";
import LocalShippingIcon from "@mui/icons-material/LocalShipping";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import MapIcon from "@mui/icons-material/Map";
import PersonIcon from "@mui/icons-material/Person";
import AssignmentIcon from "@mui/icons-material/Assignment";
import BarChartIcon from "@mui/icons-material/BarChart";
import SettingsIcon from "@mui/icons-material/Settings";
import { useNavigate, useLocation } from "react-router-dom";

const drawerWidth = 240;

const navItems = [
  { label: "Dashboard", icon: <DashboardIcon />, path: "/" },
  { label: "Trucks", icon: <LocalShippingIcon />, path: "/trucks" },
  { label: "Availability", icon: <EventAvailableIcon />, path: "/availability" },
  { label: "Bookings", icon: <AssignmentIcon />, path: "/bookings" },
  { label: "GPS Tracking", icon: <MapIcon />, path: "/gps" },
  { label: "Drivers", icon: <PersonIcon />, path: "/drivers" },
  { label: "Reports", icon: <BarChartIcon />, path: "/reports" },
  { label: "Settings", icon: <SettingsIcon />, path: "/settings" },
];

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <Drawer
      variant="permanent"
      sx={{
        width: drawerWidth,
        flexShrink: 0,
        [`& .MuiDrawer-paper`]: { width: drawerWidth, boxSizing: "border-box", bgcolor: "#0f172a", color: "#fff" },
      }}
    >
      <Toolbar>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1, py: 1 }}>
          <LocalShippingIcon />
          <Typography variant="subtitle1" fontWeight="bold">Fleet Manager</Typography>
        </Box>
      </Toolbar>
      <List>
        {navItems.map((item) => (
          <ListItemButton
            key={item.path}
            selected={location.pathname === item.path}
            onClick={() => navigate(item.path)}
            sx={{
              "&.Mui-selected": { bgcolor: "#1e293b" },
              "&:hover": { bgcolor: "#1e293b" },
            }}
          >
            <ListItemIcon sx={{ color: "#fff" }}>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
      </List>
    </Drawer>
  );
}
