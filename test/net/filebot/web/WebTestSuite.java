package net.filebot.web;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ WebServicesTest.class, OpenSubtitlesRestApiTest.class, SimpleDateTest.class, AnidbClientTest.class, AniDBHttpApiTest.class, TheTVDBClientTest.class, TheTVDBV4ApiTest.class, TVMazeClientTest.class, TMDbClientTest.class, TMDbTVClientTest.class, TMDbV3ApiTest.class, OMDbClientTest.class, OMDbV1ApiTest.class, AcoustIDClientTest.class, AcoustIDV2ApiTest.class })
public class WebTestSuite {

}
