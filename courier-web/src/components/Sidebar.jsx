import React from "react";
import {
  Drawer, List, ListItemButton, ListItemIcon, ListItemText, Toolbar, Box, Typography,
} from "@mui/material";
import DashboardIcon from "@mui/icons-material/Dashboard";
import LocalShippingIcon from "@mui/icons-material/LocalShipping";
import PeopleIcon from "@mui/icons-material/People";
import AssignmentIcon from "@mui/icons-material/Assignment";
import NotificationsIcon from "@mui/icons-material/Notifications";
import SettingsIcon from "@mui/icons-material/Settings";
import { useNavigate, useLocation } from "react-router-dom";

const drawerWidth = 240;

const navItems = [
  { label: "Dashboard", icon: <DashboardIcon />, path: "/" },
  { label: "Shipments", icon: <LocalShippingIcon />, path: "/shipments" },
  { label: "Customers", icon: <PeopleIcon />, path: "/customers" },
  { label: "Bookings", icon: <AssignmentIcon />, path: "/bookings" },
  { label: "Notifications", icon: <NotificationsIcon />, path: "/notifications" },
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
          <Typography variant="subtitle1" fontWeight="bold">Courier Portal</Typography>
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
