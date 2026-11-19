import random
import unittest

import zone_lab


def sample(base, noise, rng, mag=None, gravity=None, source="recording", **extra):
    wifi = [{"b": b, "r": r + rng.randint(-noise, noise), "f": 2412} for b, r in base.items()]
    return dict(source=source, wifi=wifi, magMean=mag, gravity=gravity, scanFresh=True, scanAgeMs=1200, **extra)


def recording_rows(dev, rec, spot, mode, base, rng, n=4, noise=2, **kw):
    return [{"device_id": dev, "type": "zone_sample", "at": None,
             "payload": sample(base, noise, rng, rec=rec, spot=spot, mode=mode, i=i, **kw)} for i in range(n)]


class ZoneLabTest(unittest.TestCase):
    def test_distance_matches_app_cases(self):
        a = [{"b": "a", "r": -50}]
        self.assertEqual(zone_lab.wifi_distance(a, a), 0)
        self.assertEqual(zone_lab.wifi_distance(a, [{"b": "a", "r": -60}]), 10)
        self.assertEqual(zone_lab.wifi_distance(a, [{"b": "c", "r": -50}]), 42)
        self.assertAlmostEqual(zone_lab.wifi_distance([{"b": "00:a", "r": -50}, {"b": "00:b", "r": -70}],
                                                      [{"b": "00:a", "r": -54}, {"b": "00:c", "r": -70}]), 4 + 20 * 2 / 3)
        self.assertAlmostEqual(zone_lab.angle_degrees([0, 0, 1], [0, 1, 0]), 90)

    def test_separable_spots_score_high_and_report_prints(self):
        rng = random.Random(1)
        desk = {"a": -45, "b": -70, "c": -80}
        bed = {"a": -70, "b": -48, "c": -60}
        rows = []
        for i in range(2):
            rows += recording_rows("d1", f"desk{i}", "Desk", "resting", desk, rng)
            rows += recording_rows("d1", f"bed{i}", "Bed", "resting", bed, rng)
        rows.append({"device_id": "d1", "type": "zone_guess_feedback", "at": None,
                     "payload": {"id": "s1", "guessed": "Desk", "actual": "Desk", "correct": True, "source": "notification"}})
        rows.append({"device_id": "d1", "type": "zone_sample", "at": None,
                     "payload": sample(desk, 2, rng, source="tracking", id="s1", guess="Desk", confidence=0.9)})
        res = zone_lab.analyse(rows)
        self.assertEqual(res["recordings"], 4)
        for name in zone_lab.FEATURE_SETS:
            cells = res["loro"][name]
            self.assertEqual(cells[("Desk", "resting")], [8, 8])
            self.assertEqual(cells[("Bed", "resting")], [8, 8])
        self.assertEqual(res["feedback"][("notification", "Desk")], [1, 1])
        self.assertEqual(res["scans"]["tracking"]["n"], 1)
        zone_lab.print_report(res)

    def test_same_wifi_spots_need_magnet(self):
        rng = random.Random(2)
        wifi = {"a": -50, "b": -60}
        rows = []
        for i in range(2):
            rows += recording_rows("d1", f"desk{i}", "Desk", "resting", wifi, rng, noise=0, mag=30.0)
            rows += recording_rows("d1", f"bed{i}", "Bed", "resting", wifi, rng, noise=0, mag=70.0)
        res = zone_lab.analyse(rows)
        total = lambda name: sum(c[0] for c in res["loro"][name].values())
        self.assertEqual(total("wifi+mag"), 16)
        self.assertLess(total("wifi"), 16)

    def test_other_devices_never_train_each_other(self):
        rng = random.Random(3)
        rows = recording_rows("d1", "r1", "Desk", "resting", {"a": -45}, rng) + recording_rows("d2", "r2", "Bed", "resting", {"a": -45}, rng)
        res = zone_lab.analyse(rows)
        self.assertEqual(res["loro"]["wifi"][("Desk", "resting")], [0, 4])


if __name__ == "__main__":
    unittest.main()
