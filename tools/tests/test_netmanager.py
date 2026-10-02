"""NetworkManager contract and real D-Bus handovers (requires python3-gi and dbus).

Run on Linux: dbus-run-session -- python3 -m unittest discover -s tools/tests -p test_netmanager.py
"""
import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

try:
    import gi
    gi.require_version("Gio", "2.0")
    from gi.repository import Gio, GLib
except ImportError:
    Gio = GLib = None

SCRIPT = Path(__file__).resolve().parents[1] / "linuxfs/overlay/usr/local/bin/bannerlator-netmanager"
if Gio:
    loader = importlib.machinery.SourceFileLoader("netmanager", str(SCRIPT))
    spec = importlib.util.spec_from_loader(loader.name, loader)
    nm = importlib.util.module_from_spec(spec)
    loader.exec_module(nm)

WIFI = {
    "connected": True, "transport": "wifi", "interface": "wlan0", "wifiEnabled": True,
    "validated": True, "captivePortal": False, "metered": False,
    "ssid": "Test \"Wi-Fi\" ☃", "strength": 73, "frequency": 5180, "bitrate": 866000,
    "addresses": [{"address": "192.0.2.2", "prefix": 24}, {"address": "2001:db8::2", "prefix": 64}],
    "gateways": ["192.0.2.1", "2001:db8::1"], "dns": ["192.0.2.1", "2001:db8::1"],
}
CELLULAR = {
    "connected": True, "transport": "cellular", "interface": "rmnet_data0", "wifiEnabled": True,
    "validated": True, "metered": True,
    "addresses": [{"address": "2001:db8:1::2", "prefix": 64}],
    "gateways": ["2001:db8:1::1"], "dns": ["2001:db8:1::1"],
}
OFFLINE = {"connected": False, "transport": "none", "wifiEnabled": True, "metered": None}


@unittest.skipUnless(Gio, "requires PyGObject")
class NetworkObjectsTest(unittest.TestCase):
    def test_wifi_graph(self):
        found = nm.objects(WIFI)
        manager = found[nm.ROOT][nm.NAME]
        device = found[nm.device_path(WIFI)]
        self.assertEqual(manager["PrimaryConnectionType"].unpack(), "802-11-wireless")
        self.assertTrue(manager["WirelessEnabled"].unpack())
        self.assertEqual(device[nm.NAME + ".Device"]["DeviceType"].unpack(), 2)
        self.assertEqual(device[nm.NAME + ".Device.Wireless"]["ActiveAccessPoint"].unpack(), nm.AP)
        ap = found[nm.AP][nm.NAME + ".AccessPoint"]
        self.assertEqual(bytes(ap["Ssid"].unpack()).decode(), WIFI["ssid"])
        self.assertEqual(ap["Strength"].unpack(), 73)
        self.assertEqual(manager["Metered"].unpack(), 2)
        for interfaces in found.values():
            for values in interfaces.values():
                for value in values.values():
                    if value.get_type_string() == "o" and value.unpack() != "/":
                        self.assertIn(value.unpack(), found)

    def test_cellular_ipv6_only(self):
        found = nm.objects(CELLULAR)
        manager = found[nm.ROOT][nm.NAME]
        device = found[nm.device_path(CELLULAR)]
        active = found[nm.ACTIVE][nm.NAME + ".Connection.Active"]
        self.assertEqual(manager["PrimaryConnectionType"].unpack(), "gsm")
        self.assertEqual(manager["Metered"].unpack(), 1)
        self.assertEqual(device[nm.NAME + ".Device"]["DeviceType"].unpack(), 8)
        self.assertIn(nm.NAME + ".Device.Modem", device)
        self.assertNotIn(nm.AP, found)
        self.assertNotIn(nm.IP4, found)
        self.assertEqual(active["Ip4Config"].unpack(), "/")
        self.assertEqual(active["Ip6Config"].unpack(), nm.IP6)
        self.assertTrue(active["Default6"].unpack())
        self.assertFalse(active["Default"].unpack())
        self.assertNotEqual(nm.device_path(WIFI), nm.device_path(CELLULAR))

    def test_offline_and_captive_portal(self):
        offline = nm.objects(OFFLINE)
        self.assertEqual(offline[nm.ROOT][nm.NAME]["State"].unpack(), 20)
        self.assertEqual(offline[nm.ROOT][nm.NAME]["ActiveConnections"].unpack(), [])
        self.assertNotIn(nm.AP, offline)
        self.assertEqual(nm.connectivity(dict(WIFI, validated=False, captivePortal=True)), 2)
        self.assertEqual(nm.connectivity(dict(WIFI, validated=False, captivePortal=False)), 3)
        self.assertEqual(nm.metered(OFFLINE), 0)

    def test_redacted_name_and_wired_vpn(self):
        redacted = dict(WIFI)
        del redacted["ssid"]
        self.assertEqual(nm.connection_name(redacted), "Wi-Fi")
        self.assertEqual(bytes(nm.settings(redacted)["802-11-wireless"]["ssid"].unpack()), b"Wi-Fi")
        for transport, kind, device_type in (("ethernet", "802-3-ethernet", 1), ("vpn", "tun", 16)):
            state = dict(WIFI, transport=transport)
            found = nm.objects(state)
            self.assertEqual(found[nm.ROOT][nm.NAME]["PrimaryConnectionType"].unpack(), kind)
            self.assertEqual(found[nm.device_path(state)][nm.NAME + ".Device"]["DeviceType"].unpack(), device_type)
            self.assertNotIn(nm.AP, found)


@unittest.skipUnless(Gio and os.environ.get("DBUS_SESSION_BUS_ADDRESS"), "requires dbus-run-session and PyGObject")
class NetworkBusTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.snapshot = Path(self.temp.name) / "network.json"
        self.write(WIFI)
        self.bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
        env = dict(os.environ, DBUS_SYSTEM_BUS_ADDRESS=os.environ["DBUS_SESSION_BUS_ADDRESS"])
        # Use the production service and only redirect its input file for the fixture.
        launch = "import runpy,sys; from pathlib import Path; n=runpy.run_path(sys.argv[1]); n['main'].__globals__['SNAPSHOT']=Path(sys.argv[2]); n['main']()"
        self.process = subprocess.Popen([sys.executable, "-c", launch, str(SCRIPT), str(self.snapshot)], env=env,
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.await_type("802-11-wireless")

    def tearDown(self):
        self.process.terminate()
        _, stderr = self.process.communicate(timeout=5)
        self.temp.cleanup()
        self.assertEqual(stderr, b"", stderr.decode())

    def write(self, state):
        staged = self.snapshot.with_suffix(".staged")
        staged.write_text(json.dumps(state))
        staged.replace(self.snapshot)

    def call(self, path, interface, method, parameters=None):
        return self.bus.call_sync(nm.NAME, path, interface, method, parameters, None,
                                  Gio.DBusCallFlags.NONE, 1000, None).unpack()

    def prop(self, path, interface, name):
        return self.call(path, nm.PROPERTIES, "Get", GLib.Variant("(ss)", (interface, name)))[0]

    def await_type(self, expected):
        deadline = time.monotonic() + 6
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                self.fail(self.process.communicate()[1].decode())
            try:
                if self.prop(nm.ROOT, nm.NAME, "PrimaryConnectionType") == expected:
                    return
            except GLib.Error:
                pass
            time.sleep(0.05)
        self.fail("network type did not change to " + expected)

    def test_direct_properties_methods_and_handover(self):
        signals = []
        subscription = self.bus.signal_subscribe(
            nm.NAME, None, None, None, None, Gio.DBusSignalFlags.NONE,
            lambda bus, sender, path, interface, member, args: signals.append((path, interface, member, args.unpack())))
        self.addCleanup(self.bus.signal_unsubscribe, subscription)
        wifi_path = nm.device_path(WIFI)
        self.assertEqual(self.prop(wifi_path, nm.NAME + ".Device", "DeviceType"), 2)
        self.assertEqual(self.prop(nm.AP, nm.NAME + ".AccessPoint", "Strength"), 73)
        self.assertEqual(self.call(wifi_path, nm.NAME + ".Device.Wireless", "GetAccessPoints"), ([nm.AP],))
        self.assertEqual(self.call(wifi_path, nm.NAME + ".Device.Wireless", "RequestScan", GLib.Variant("(a{sv})", ({},))), ())
        all_props = self.call(nm.ROOT, nm.PROPERTIES, "GetAll", GLib.Variant("(s)", (nm.NAME,)))[0]
        self.assertTrue(all_props["WirelessEnabled"])
        introspection = self.call(wifi_path, "org.freedesktop.DBus.Introspectable", "Introspect")[0]
        self.assertIn("GetAllAccessPoints", introspection)
        self.write(dict(WIFI, strength=21, ssid="Other Wi-Fi"))
        deadline = time.monotonic() + 4
        while self.prop(nm.AP, nm.NAME + ".AccessPoint", "Strength") != 21 and time.monotonic() < deadline:
            time.sleep(0.05)
        self.assertEqual(self.prop(nm.AP, nm.NAME + ".AccessPoint", "Strength"), 21)
        self.assertEqual(self.call(nm.PROFILE, nm.NAME + ".Settings.Connection", "GetSettings")[0]["connection"]["id"], "Other Wi-Fi")
        self.write(CELLULAR)
        self.await_type("gsm")
        cell_path = nm.device_path(CELLULAR)
        self.assertEqual(self.call(nm.ROOT, nm.NAME, "GetDevices"), ([cell_path],))
        self.assertEqual(self.prop(cell_path, nm.NAME + ".Device", "DeviceType"), 8)
        self.assertEqual(self.prop(nm.ROOT, nm.NAME, "Metered"), 1)
        managed = self.call(nm.ROOT, nm.OBJECT_MANAGER, "GetManagedObjects")[0]
        self.assertNotIn(wifi_path, managed)
        self.assertNotIn(nm.AP, managed)
        self.assertNotIn(nm.IP4, managed)
        self.assertIn(nm.IP6, managed)
        with self.assertRaises(GLib.Error):
            self.prop(wifi_path, nm.NAME + ".Device", "DeviceType")
        self.write(OFFLINE)
        self.await_type("")
        self.assertEqual(self.call(nm.ROOT, nm.NAME, "CheckConnectivity"), (1,))
        self.write(WIFI)
        self.await_type("802-11-wireless")
        self.assertEqual(self.prop(nm.AP, nm.NAME + ".AccessPoint", "Strength"), 73)
        context = GLib.MainContext.default()
        while context.pending():
            context.iteration(False)
        self.assertTrue(any(interface == nm.PROPERTIES and member == "PropertiesChanged" and
                            args[1].get("PrimaryConnectionType") == "gsm"
                            for _, interface, member, args in signals))
        self.assertTrue(any(interface == nm.OBJECT_MANAGER and member == "InterfacesRemoved" and args[0] == wifi_path
                            for _, interface, member, args in signals))
        self.assertTrue(any(member == "AccessPointAdded" for _, _, member, _ in signals))
        # A missing/invalid snapshot is a transient input error, never a fake Ethernet handover.
        self.snapshot.write_text("{")
        time.sleep(1.2)
        self.assertEqual(self.prop(nm.ROOT, nm.NAME, "PrimaryConnectionType"), "802-11-wireless")

    def test_libnm_client(self):
        try:
            gi.require_version("NM", "1.0")
            from gi.repository import NM
        except ValueError:
            self.skipTest("requires gir1.2-nm-1.0")
        # libnm is a real consumer of the same API Steam reads. It caches objects by path,
        # so the handover must replace the Wi-Fi device with a modem rather than mutate its type.
        previous = os.environ.get("DBUS_SYSTEM_BUS_ADDRESS")
        os.environ["DBUS_SYSTEM_BUS_ADDRESS"] = os.environ["DBUS_SESSION_BUS_ADDRESS"]
        try:
            client = NM.Client.new(None)
            self.assertTrue(client.get_nm_running())
            wifi = client.get_devices()[0]
            self.assertIsInstance(wifi, NM.DeviceWifi)
            self.assertEqual(wifi.get_active_access_point().get_strength(), 73)
            self.assertEqual(client.get_primary_connection().get_connection_type(), "802-11-wireless")
            self.write(CELLULAR)
            self.await_type("gsm")
            context = GLib.MainContext.default()
            deadline = time.monotonic() + 4
            while time.monotonic() < deadline:
                while context.pending():
                    context.iteration(False)
                if client.get_devices() and isinstance(client.get_devices()[0], NM.DeviceModem):
                    break
                time.sleep(0.05)
            self.assertIsInstance(client.get_devices()[0], NM.DeviceModem)
            self.assertEqual(client.get_primary_connection().get_connection_type(), "gsm")
        finally:
            if previous is None:
                os.environ.pop("DBUS_SYSTEM_BUS_ADDRESS", None)
            else:
                os.environ["DBUS_SYSTEM_BUS_ADDRESS"] = previous


if __name__ == "__main__":
    unittest.main()
