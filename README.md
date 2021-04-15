# Auto Pilot

AutoPilot is an experimental android app developed for semi-autonomous navigation in (RCV) remote-controlled vehicles.
AutoPilot relies on inboard sensors like Camera, Motion, Position and Environmental sensors to navigate.

Communication Protocol

char('L') + 8 Bytes Double(Latitude, Longitude, Altitude) {IEEE 754} 
char('A') + 2 Bytes Short(Latitude, Longitude, Altitude) 
char('O') + 2 Bytes Short(Yaw, Pitch, Roll) 
char('C') + 2 Bytes Short(ImageSize = x) + x Bytes Byte(JPEG Image) 
char('R') + 2 Bytes Short(RPM) 
