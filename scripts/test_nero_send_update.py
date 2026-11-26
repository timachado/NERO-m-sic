import unittest
from nero_send_update import build_payload

class NotificationContract(unittest.TestCase):
    def setUp(self):
        self.row=dict(applicationId='br.com.timachado.neromusic',channel='stable',published=True,versionCode=167,versionName='1.0.0',downloadUrl='https://github.com/timachado/NERO-m-sic/releases/download/v1.0.0/NERO-Music.apk',sha256='a'*64,publishedAt='2026-11-26T12:00:00Z')
    def test_exclusive_topic_and_minimal_payload(self):
        msg=build_payload(self.row)['message']
        self.assertEqual(msg['topic'],'nero_music_stable_updates_v1')
        self.assertEqual(msg['data'],dict(kind='nero_update_v1',applicationId='br.com.timachado.neromusic',versionCode='167'))
        self.assertNotIn('notification',msg)
    def test_blocks_invalid_publication(self):
        for invalid in [dict(published=False),dict(channel='beta'),dict(applicationId='com.timachado.brothermatrizes'),dict(versionCode=0),dict(versionCode=True),dict(versionName='1.0.0-dev'),dict(sha256='invalid'),dict(downloadUrl='https://example.org/fake.apk'),dict(publishedAt='')]:
            with self.subTest(invalid=invalid):
                with self.assertRaises(AssertionError):build_payload(self.row|invalid)

if __name__=='__main__':unittest.main()
