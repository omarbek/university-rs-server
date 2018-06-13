package kz.halyqsoft.univercity.rs.server;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Author: Omarbek Dinassil
 * Created: 22.03.2018 09:10
 */
@Path("/service")
public class UnivercityService {

    private Connection connection;

    public UnivercityService() {
    }

    @GET
    @Path("/hello")
    public Response getHello() {
        return Response.ok("Hello university").build();
    }

    @POST
    @Path("/user_arrival")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserArrival(String data) {
        begin("University web service: arrivals of users", data);

        String message = getMessage(data);

        return Response.ok(message).build();
    }

    @POST
    @Path("/login")
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(String data) {
        JSONObject jsonObject = new JSONObject(data);
        JSONObject json = new JSONObject();

        begin("University web service: login", data);

        System.out.println(connect());

        try {
            List<Object> dataList = new ArrayList<Object>();
            if (isAvailable(jsonObject)) {
                dataList = getListByQuery(jsonObject.get("query").toString());
                json.put("my_profile", dataList);
            }
            json.put("my_profile", dataList);
        } catch (Exception e) {
            getMessage(e, "Unable to check or insert");
        }
        return Response.ok(json.toString()).build();
    }

    @POST
    @Path("/photo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPhoto(String data) {
        JSONObject jsonObject = new JSONObject(data);
        JSONObject json = new JSONObject();

        begin("University web service: photo", data);

        System.out.println(connect());

        try {
            if (isAvailable(jsonObject)) {
                byte[] photo = getPhotoByteArray(jsonObject);
                String byteArrayAsString = Base64.encodeBase64String(photo);
                json.put("photo", byteArrayAsString);
            }
        } catch (Exception e) {
            getMessage(e, "Unable to check or insert");
        }
        return Response.ok(json.toString()).build();
    }

    private byte[] getPhotoByteArray(JSONObject jsonObject) throws Exception {
        Object login = jsonObject.get("user_login");
        String sql = "select ph.photo from users usr" +
                " inner join user_photo ph on ph.user_id = usr.id" +
                " where usr.login = ?";
        PreparedStatement userPS = connection.prepareStatement(sql);
        userPS.setString(1, login.toString());
        ResultSet userRS = userPS.executeQuery();
        byte[] photo = new byte[0];
        if (userRS.next()) {
            photo = userRS.getBytes("photo");
        }
        return photo;
    }

    private List<Object> getListByQuery(String query) throws Exception {
        List<Object> list = new ArrayList<Object>();

        if (connection != null) {
            System.out.println("Connected to the database");
            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery(query);
            while (rs.next()) {
                int count = getNumberOfFields(query);
                for (int i = 0; i < count; i++) {
                    String string = rs.getString(i + 1);
                    list.add(string);
                }
            }
        }
        return list;
    }

    private int getNumberOfFields(String query) {
        String fields = StringUtils.substringBetween(query, "select", "from");
        StringTokenizer stringTokenizer = new StringTokenizer(fields.trim(), ",");
        int count = 0;
        while (stringTokenizer.hasMoreElements()) {
            stringTokenizer.nextElement();
            count++;
        }
        return count;
    }

    private void begin(String title, String data) {
        System.out.println(title);
        System.out.println(data);
    }

    private String getMessage(String data) {
        JSONObject jsonObject = new JSONObject(data);
        String connectionMessage = connect();
        if (connectionMessage != null) return connectionMessage;

        String insertMessage = insert(jsonObject);
        if (insertMessage != null) return insertMessage;

        String closeMessage = close();
        if (closeMessage != null) return closeMessage;

        return "successful";
    }

    private String close() {
        if (connection != null) {
            System.out.println("You made it, take control your database now!");
            try {
                connection.close();
            } catch (SQLException e) {
                return getMessage(e, "Couldn't close the connection");
            }
        } else {
            System.out.println("Failed to make connection!");
        }
        return null;
    }

    private String insert(JSONObject jsonObject) {
        try {
            if (isAvailable(jsonObject)) {
                insertToDb(jsonObject);
            } else {
                return getMessage(null, "You have no access to the service!");
            }
        } catch (Exception e) {
            return getMessage(e, "Unable to check or insert");
        }
        return null;
    }

    private String connect() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            return getMessage(e, "Where is your PostgreSQL JDBC Driver? Include in your library path!");
        }

        System.out.println("PostgreSQL JDBC Driver Registered!");

        try {
            connection = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/univerdb", "postgres",
                    "ukpu18!");
        } catch (SQLException e) {
            return getMessage(e, "Connection Failed! Check output console");
        }
        return null;
    }

    private String getMessage(Exception e, String message) {
        if (e != null) {
            e.printStackTrace();
        }
        System.out.println(message);
        return message;
    }

    private void insertToDb(JSONObject jsonObject) throws SQLException {
        Object userCode = jsonObject.get("user_code");
        Object turnstileTypeId = jsonObject.get("turnstile_type_id");
        int turnstileType = Integer.parseInt(turnstileTypeId.toString());
        if (turnstileType == 5) {
            String deleteFromDeletedUsersSql = "update users set card_id = null where deleted=true";
            PreparedStatement deleteFromDeletedUsersPS = connection.prepareCall(deleteFromDeletedUsersSql);
            deleteFromDeletedUsersPS.executeUpdate();

            String deleteSql = "DELETE FROM card " +
                    "WHERE id NOT IN " +
                    "      (SELECT card_id " +
                    "       FROM users " +
                    "       WHERE deleted = FALSE AND card_id IS NOT NULL)";
            PreparedStatement deletePS = connection.prepareStatement(deleteSql);
            deletePS.executeUpdate();

            String sql = "INSERT INTO card VALUES (nextval('s_card'), ?)";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, userCode.toString());
            preparedStatement.executeUpdate();
        } else {
            Object comeIn = jsonObject.get("come_in");
            String sql = "INSERT INTO user_arrival VALUES (nextval('s_user_arrival'), ?, now(), ?, ?)";

            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            int userId = getUserId(userCode);
            System.out.println("user_id: " + userId);
            preparedStatement.setInt(1, userId);
            preparedStatement.setBoolean(2, Boolean.valueOf(comeIn.toString()));
            preparedStatement.setInt(3, turnstileType);
            preparedStatement.executeUpdate();
        }
    }

    private int getUserId(Object cardName) throws SQLException {
        String sql = "select usr.id from users usr" +
                " inner join card card on card.id = usr.card_id" +
                " where card.card_name = ?";
        PreparedStatement userPS = connection.prepareStatement(sql);
        userPS.setString(1, cardName.toString());
        ResultSet userRS = userPS.executeQuery();
        int id = 0;
        if (userRS.next()) {
            id = userRS.getInt("id");
        }
        return id;
    }

    private boolean isAvailable(JSONObject jsonObject) throws SQLException {
        Object password = jsonObject.get("passwd");
        Object login = jsonObject.get("login");
        String checkSQL = "select 1 from users where login=? and passwd=? and deleted=false";

        PreparedStatement preparedStatement = connection.prepareStatement(checkSQL);
        preparedStatement.setString(1, login.toString());
        preparedStatement.setString(2, password.toString());
        ResultSet resultSet = preparedStatement.executeQuery();
        return resultSet.next();
    }
}
