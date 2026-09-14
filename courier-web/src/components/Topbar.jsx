import React from "react";
import { AppBar, Toolbar, Typography, IconButton, Avatar, Box } from "@mui/material";
import NotificationsNoneIcon from "@mui/icons-material/NotificationsNone";

export default function Topbar({ title = "Dashboard" }) {
  return (
    <AppBar
      position="fixed"
      color="inherit"
      elevation={0}
      sx={{ ml: "240px", width: "calc(100% - 240px)", borderBottom: "1px solid #e2e8f0" }}
    >
      <Toolbar sx={{ justifyContent: "space-between" }}>
        <Typography variant="h6" fontWeight={600}>{title}</Typography>
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <IconButton>
            <NotificationsNoneIcon />
          </IconButton>
          <Avatar sx={{ width: 32, height: 32 }}>C</Avatar>
        </Box>
      </Toolbar>
    </AppBar>
  );
}
