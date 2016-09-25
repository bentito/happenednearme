package imaggaclassifier;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.json.JSONArray;
import twitter4j.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Created by btofel on 9/24/16 for HackGT
 */
public class ImaggaClassifier {
    public static void main(String[] args) throws Exception {
        // The factory instance is re-useable and thread safe.
        Twitter twitter = TwitterFactory.getSingleton();
        Query query = new Query("geocode:-22.912214,-43.230182,1km filter:twimg");
        QueryResult result = twitter.search(query);
        for (Status status : result.getTweets()) {
            System.out.println("@" + status.getUser().getScreenName() + ":" + status.getText());
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/requests", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            System.out.println("Serving the request");
            try {
                HashMap rh = new HashMap();

                Headers eh = he.getRequestHeaders();
                for (Map.Entry<String, List<String>> e : eh.entrySet()) {
                    rh.put(e.getKey(), e.getValue().get(0));
                }
                fakeInsta(Float.parseFloat((String)rh.get("Latitude")), Float.parseFloat((String)rh.get("Longitude")));
            } catch (Exception e) {
                System.out.println("ERROR: " + e);
                e.printStackTrace();
            }
        }
    }

    static void fakeInsta(Float lat, Float lon) {
        ArrayList instaUrls = new ArrayList();
        ArrayList classfierJSONs = new ArrayList();
        instaUrls.add("https://www.royalcanin.com/~/media/Royal-Canin/Product-Categories/cat-adult-landing-hero.ashx");
        instaUrls.add("https://images-na.ssl-images-amazon.com/images/G/01/img15/pet-products/small-tiles/23695_pets_vertical_store_dogs_small_tile_8._CB312176604_.jpg");
        HashMap myMap = instaClassify(instaUrls);
        System.out.println(new PrettyPrintingMap<String, String>(myMap));
        hashToTextToImg(myMap); // save wordFreqs.txt
    }

    static HashMap instaClassify(ArrayList instaUrls) {
        ArrayList classfierJSONs = new ArrayList();
        String apiKey = "acc_db2e8c1135859a7", apiSecret = "0edb16800ccf9aaf1f6c14c35993eb70";
        HashMap classMap = new HashMap();
        try {
            Iterator itr = instaUrls.iterator();
            while (itr.hasNext()) {
                HttpResponse response = Unirest.get("https://api.imagga.com/v1/tagging")
                        .queryString("url", itr.next())
                        .basicAuth(apiKey, apiSecret)
                        .header("Accept", "application/json")
                        .asJson();
                HttpResponse<JsonNode> jsonResponse = response;
                JSONArray ary = jsonResponse.getBody().getObject().getJSONArray("results");
                JSONObject obj1 = (JSONObject) ary.get(0); //hackey hack hackathon
                JSONArray ary2 = (JSONArray) obj1.get("tags");
                classfierJSONs.add(ary2);
            }
            classMap = jsonsToMap(classfierJSONs);
        } catch (UnirestException e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
        return classMap;
    }

    static HashMap jsonsToMap(ArrayList classConfidenceJSONArrays) {
        HashMap<String, Integer> ret = new HashMap();
        Iterator itr = classConfidenceJSONArrays.iterator();
        int prev = 0;
        while (itr.hasNext()) {
            JSONArray classConfidenceJSONArray = (JSONArray) itr.next();
            Iterator iitr = classConfidenceJSONArray.iterator(); // there's an annoying inner arraylist to deal with
            while (iitr.hasNext()) {
                JSONObject confTag = (JSONObject) iitr.next();
                String tag = (String) confTag.get("tag");
                float confidence = Float.parseFloat(confTag.get("confidence").toString());
                Integer conf = Math.round(confidence);
                if (!ret.containsKey(tag)) {
                    ret.put(tag, conf);
                } else {
                    prev = (int) ret.get(tag);
                    ret.put(tag, conf + prev);
                }
            }

        }
        return ret;
    }

    public static void hashToTextToImg(Map<String, Integer> data) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter("wordFreqs.txt", "UTF-8");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
        Iterator it = data.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            String word = pair.getKey() + " ";
            Integer frequency = (Integer) pair.getValue();
            for (int i = 0; i < frequency; i++) {
                writer.println(word);
            }
        }
        writer.close();
//        CloudRenderer warandpeace = new CloudRenderer("warandpeace.txt", SourceType.FILE, DrawingPattern.GAUSSIAN, "tolstoy");
//        warandpeace.draw();
    }
}
