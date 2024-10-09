package matypist.openstud.driver.core.providers.sapienza;

import matypist.openstud.driver.core.Openstud;
import matypist.openstud.driver.core.internals.AuthenticationHandler;
import matypist.openstud.driver.exceptions.*;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class SapienzaAuthenticationHandler implements AuthenticationHandler {
    private Openstud os;

    public SapienzaAuthenticationHandler(Openstud openstud) {
        this.os = openstud;
    }

    private String getTokenFromResponse(JSONObject response) throws OpenstudInvalidResponseException {
        String token = "";

        if (response.has("output")) {
            if (!response.isNull("output")) {
                token = response.getString("output");
            }
        } else if (response.has("result")) {
            if (!response.isNull("result")) {
                JSONObject result = response.getJSONObject("result");

                if(result.has("tokeniws") && !result.isNull("tokeniws")) {
                    token = result.getString("tokeniws");
                }
            }
        } else throw new OpenstudInvalidResponseException("Infostud answer is not valid");

        return token;
    }

    @Override
    public synchronized void refreshToken() throws OpenstudRefreshException, OpenstudInvalidResponseException {
        try {
            if (!StringUtils.isNumeric(os.getStudentID()))
                throw new OpenstudRefreshException("Student ID is not valid");
            String body = executeLoginRequest();
            JSONObject response = new JSONObject(body);

            String token = getTokenFromResponse(response);
            if(!token.isEmpty()) os.setToken(token);

            if (body.toLowerCase().contains("utenza bloccata")) throw new OpenstudRefreshException("Account is blocked").setAccountBlockedType();

            if (response.has("esito")) {
                switch (response.getJSONObject("esito").getInt("flagEsito")) {
                    case -6:
                        if (response.getJSONObject("esito").getBoolean("captcha")) throw new OpenstudRefreshException("Captcha required");
                        else throw new OpenstudInvalidResponseException("Infostud is not working as intended");
                    case -4:
                        throw new OpenstudRefreshException("User is not enabled to use Infostud service");
                    case -2:
                        throw new OpenstudRefreshException("Password expired").setPasswordExpiredType();
                    case -1:
                        throw new OpenstudRefreshException("Invalid credentials when refreshing token").setPasswordInvalidType();
                    case 0:
                        break;
                    default:
                        throw new OpenstudInvalidResponseException("Infostud is not working as intended");
                }
            } else if(response.has("error")) {
                switch (response.getJSONObject("error").getString("code")) {
                    case "auth110":
                        throw new OpenstudRefreshException("Invalid credentials when refreshing token").setPasswordInvalidType();
                    case "auth151":
                        throw new OpenstudRefreshException("User is not enabled to use Infostud service.");
                    case "auth500":
                        throw new OpenstudInvalidResponseException("Infostud is not working as intended");
                    case "0":
                        break;
                    default:
                        throw new OpenstudInvalidResponseException("Infostud is not working as expected");
                }
            } else if(token.isEmpty()) {
                throw new OpenstudInvalidResponseException("Infostud is not working as expected");
            }
        } catch (IOException | JSONException e) {
            os.log(Level.SEVERE, e);
            e.printStackTrace();
        }
    }

    private Response executeLegacyLoginRequest() throws IOException {
        // {os.getEndpointAPI()}/autenticazione?matricola={os.getStudentID()}&stringaAutenticazione={os.getStudentPassword()}

        RequestBody formBody = new FormBody.Builder()
                .add("key", os.getKey()).add("matricola", os.getStudentID()).add("stringaAutenticazione", os.getStudentPassword()).build();

        Request req = new Request.Builder().url(String.format("%s/autenticazione", os.getEndpointAPI())).header("Accept", "application/json")
                .header("Content-EventType", "application/x-www-form-urlencoded").post(formBody).build();

        Response resp = os.getClient().newCall(req).execute();

        return resp;
    }

    private Response executeIDMLoginRequest() throws IOException {
        /*
        {
           request: {
            user: os.getStudentID()
            passw: os.getStudentPassword()
           }
           id: null
        }
        */

        JSONObject requestObj = new JSONObject();
        requestObj.put("user", os.getStudentID());
        requestObj.put("passwd", os.getStudentPassword());

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("request", requestObj);
        jsonBody.put("id", JSONObject.NULL);

        RequestBody reqBody = RequestBody.create(
                jsonBody.toString().getBytes(StandardCharsets.UTF_8),
                MediaType.get("application/json")
        );

        Request req = new Request.Builder().url(os.getEndpointLogin())
                .header("Content-Type", "application/json")
                .post(reqBody)
                .build();

        Response resp = os.getClient().newCall(req).execute();

        return resp;
    }

    private String executeLoginRequest() throws IOException, OpenstudInvalidResponseException {
        Response resp = null;

        try {
            resp = executeIDMLoginRequest();
        } catch(IOException ignored) {}

        String body = "";

        if (resp != null && resp.body() != null) {
            body = resp.body().string();

            if (!body.contains("result") || (!body.contains("tokeniws") && !body.contains("error")) || body.contains("Forbidden")) {
                resp.close();

                resp = executeLegacyLoginRequest();

                if (resp.body() != null) body = resp.body().string();
            }
        } else {
            if(resp != null) resp.close();

            resp = executeLegacyLoginRequest();

            if (resp.body() != null) body = resp.body().string();
        }

        if(body.isEmpty()) {
            throw new OpenstudInvalidResponseException("Infostud answer is not valid");
        }

        resp.close();
        if (body.contains("the page you are looking for is currently unavailable"))
            throw new OpenstudInvalidResponseException("InfoStud is in maintenance").setMaintenanceType();

        return body;
    }

    @Override
    public String getSecurityQuestion() throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException {
        int count = 0;
        if (os.getStudentID() == null) throw new OpenstudInvalidResponseException("StudentID can't be left empty");
        while (true) {
            try {
                return _getSecurityQuestion();
            } catch (OpenstudInvalidResponseException e) {
                if (e.isMaintenance()) throw e;
                if (++count == os.getMaxTries()) {
                    os.log(Level.SEVERE, e);
                    throw e;
                }
            }
        }
    }

    private String _getSecurityQuestion() throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException {
        try {
            RequestBody formBody = new FormBody.Builder()
                    .add("matricola", String.valueOf(os.getStudentID())).build();
            Request req = new Request.Builder().url(String.format("%s/pwd/recuperaDomanda/matricola/", os.getEndpointAPI())).header("Accept", "application/json")
                    .header("Content-EventType", "application/x-www-form-urlencoded").post(formBody).build();
            Response resp = os.getClient().newCall(req).execute();
            if (resp.body() == null) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            String body = resp.body().string();
            resp.close();
            if (body.contains("the page you are looking for is currently unavailable"))
                throw new OpenstudInvalidResponseException("InfoStud is in maintenance").setMaintenanceType();
            if (body.contains("Matricola Errata")) throw new OpenstudInvalidCredentialsException("Invalid studentID");
            if (body.contains("Impossibile recuperare la password per email")) return null;
            os.log(Level.INFO, body);
            JSONObject response = new JSONObject(body);
            if (response.isNull("risultato"))
                throw new OpenstudInvalidResponseException("Infostud response is not valid.");
            return response.getString("risultato");
        } catch (IOException e) {
            os.log(Level.SEVERE, e);
            throw new OpenstudConnectionException(e);
        } catch (JSONException e) {
            OpenstudInvalidResponseException invalidResponse = new OpenstudInvalidResponseException(e);
            os.log(Level.SEVERE, invalidResponse);
            throw invalidResponse;
        }
    }

    @Override
    public boolean recoverPassword(String answer) throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException, OpenstudInvalidAnswerException {
        int count = 0;
        if (os.getStudentID() == null) throw new OpenstudInvalidResponseException("StudentID can't be left empty");
        while (true) {
            try {
                return _recoverPassword(answer);
            } catch (OpenstudInvalidResponseException e) {
                if (e.isMaintenance()) throw e;
                if (++count == os.getMaxTries()) {
                    os.log(Level.SEVERE, e);
                    throw e;
                }
            }
        }
    }

    private boolean _recoverPassword(String answer) throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException, OpenstudInvalidAnswerException {
        try {
            RequestBody formBody = new FormBody.Builder()
                    .add("matricola", String.valueOf(os.getStudentID())).add("risposta", answer).build();
            String body = executeRecoveryRequest(formBody);
            if (body.contains("the page you are looking for is currently unavailable"))
                throw new OpenstudInvalidResponseException("InfoStud is in maintenance").setMaintenanceType();
            if (body.contains("Matricola Errata")) throw new OpenstudInvalidCredentialsException("Invalid studentID");
            if (body.contains("Impossibile recuperare la password per email")) return false;
            os.log(Level.INFO, body);
            JSONObject response = new JSONObject(body);
            if (response.isNull("livelloErrore"))
                throw new OpenstudInvalidResponseException("Infostud response is not valid.");
            switch (response.getInt("livelloErrore")) {
                case 3:
                    throw new OpenstudInvalidAnswerException("Answer is not correct");
                case 0:
                    break;
                default:
                    throw new OpenstudInvalidResponseException("Infostud is not working as expected");
            }
        } catch (IOException e) {
            os.log(Level.SEVERE, e);
            throw new OpenstudConnectionException(e);
        } catch (JSONException e) {
            OpenstudInvalidResponseException invalidResponse = new OpenstudInvalidResponseException(e);
            os.log(Level.SEVERE, invalidResponse);
            throw invalidResponse;
        }
        return true;
    }

    private String executeRecoveryRequest(RequestBody formBody) throws IOException, OpenstudInvalidResponseException {
        Request req = new Request.Builder().url(String.format("%s/pwd/recupera/matricola", os.getEndpointAPI())).header("Accept", "application/json")
                .header("Content-EventType", "application/x-www-form-urlencoded").post(formBody).build();
        Response resp = os.getClient().newCall(req).execute();
        if (resp.body() == null) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
        return resp.body().string();
    }

    @Override
    public void resetPassword(String new_password) throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException {
        int count = 0;
        if (os.getStudentID() == null) throw new OpenstudInvalidResponseException("StudentID can't be left empty");
        while (true) {
            try {
                _resetPassword(new_password);
                break;
            } catch (OpenstudInvalidResponseException e) {
                if (++count == os.getMaxTries()) {
                    os.log(Level.SEVERE, e);
                    throw e;
                }
            }
        }
    }

    private void _resetPassword(String new_password) throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException {
        try {
            RequestBody formBody = new FormBody.Builder()
                    .add("oldPwd", os.getStudentPassword())
                    .add("newPwd", new_password)
                    .add("confermaPwd", new_password)
                    .build();

            Request req = new Request.Builder().url(String.format("%s/pwd/%s/reset?ingresso=%s", os.getEndpointAPI(), os.getStudentID(), os.getToken())).header("Accept", "application/json")
                    .header("Content-EventType", "application/x-www-form-urlencoded").post(formBody).build();
            Response resp = os.getClient().newCall(req).execute();
            if (resp.body() == null) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            String body = resp.body().string();
            resp.close();
            os.log(Level.INFO, body);
            JSONObject response = new JSONObject(body);
            if (response.isNull("codiceErrore") || response.isNull("risultato"))
                throw new OpenstudInvalidResponseException("Infostud response is not valid.");

            String error_code = response.getString("codiceErrore");
            boolean result = response.getBoolean("risultato");
            if (error_code.equals("000") && result) {
                os.setStudentPassword(new_password);
                return;
            }

            throw new OpenstudInvalidCredentialsException("Answer is not correct");

        } catch (IOException e) {
            os.log(Level.SEVERE, e);
            throw new OpenstudConnectionException(e);
        } catch (JSONException e) {
            OpenstudInvalidResponseException invalidResponse = new OpenstudInvalidResponseException(e);
            os.log(Level.SEVERE, invalidResponse);
            throw invalidResponse;
        }
    }

    public boolean recoverPasswordWithEmail(String email, String answer) throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException, OpenstudInvalidAnswerException {
        int count = 0;
        if (os.getStudentID() == null) throw new OpenstudInvalidResponseException("StudentID can't be left empty");
        while (true) {
            try {
                return _recoverPasswordWithEmail(email, answer);
            } catch (OpenstudInvalidResponseException e) {
                if (++count == os.getMaxTries()) {
                    os.log(Level.SEVERE, e);
                    throw e;
                }
            }
        }
    }

    private boolean _recoverPasswordWithEmail(String email, String answer) throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException, OpenstudInvalidAnswerException {
        try {
            RequestBody formBody = new FormBody.Builder()
                    .add("matricola", String.valueOf(os.getStudentID())).add("email", email).add("risposta", answer).build();
            String body = executeRecoveryRequest(formBody);
            if (body.contains("Matricola Errata")) throw new OpenstudInvalidCredentialsException("Invalid studentID");
            if (body.contains("Impossibile recuperare la password per email")) return false;
            os.log(Level.INFO, body);
            JSONObject response = new JSONObject(body);
            if (response.isNull("livelloErrore"))
                throw new OpenstudInvalidResponseException("Infostud response is not valid.");
            switch (response.getInt("livelloErrore")) {
                case 3:
                    throw new OpenstudInvalidAnswerException("Answer is not correct");
                case 0:
                    break;
                default:
                    throw new OpenstudInvalidResponseException("Infostud is not working as expected");
            }
        } catch (IOException e) {
            os.log(Level.SEVERE, e);
            throw new OpenstudConnectionException(e);
        } catch (JSONException e) {
            OpenstudInvalidResponseException invalidResponse = new OpenstudInvalidResponseException(e);
            os.log(Level.SEVERE, invalidResponse);
            throw invalidResponse;
        }
        return true;
    }


    public void login() throws OpenstudInvalidCredentialsException, OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudUserNotEnabledException {
        int count = 0;
        if (os.getStudentPassword() == null || os.getStudentPassword().isEmpty())
            throw new OpenstudInvalidCredentialsException("Password can't be left empty");
        if (os.getStudentID() == null) throw new OpenstudInvalidResponseException("StudentID can't be left empty");
        while (true) {
            try {
                _login();
                break;
            } catch (OpenstudInvalidResponseException e) {
                if (++count == os.getMaxTries()) {
                    os.log(Level.SEVERE, e);
                    throw e;
                }
            }
        }
    }

    private synchronized void _login() throws OpenstudInvalidCredentialsException, OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudUserNotEnabledException {
        try {
            if (!StringUtils.isNumeric(os.getStudentID()))
                throw new OpenstudInvalidCredentialsException("Student ID is not valid");
            String body = executeLoginRequest();
            os.log(Level.INFO, body);

            JSONObject response = new JSONObject(body);

            String token = getTokenFromResponse(response);
            if(!token.isEmpty()) os.setToken(token);

            if (body.toLowerCase().contains("password errata")) {
                OpenstudInvalidCredentialsException e = new OpenstudInvalidCredentialsException("Credentials are not valid");
                String out = StringUtils.substringBetween(body.toLowerCase(), "tentativo", "effettuato").trim();
                if (out.contains("/")) {
                    String[] elements = out.split("/");
                    try {
                        if (elements.length == 2) {
                            e.setAttemptNumber(Integer.parseInt(elements[0]));
                            e.setMaxAttempts(Integer.parseInt(elements[1]));
                        }
                    } catch (NumberFormatException ignored) {

                    }
                }
                throw e;
            } else if (body.toLowerCase().contains("invalid credentials")) {
                throw new OpenstudInvalidCredentialsException("Credentials are not valid");
            } else if (body.toLowerCase().contains("utenza bloccata")) {
                throw new OpenstudInvalidCredentialsException("Account is blocked").setAccountBlockedType();
            }

            if (response.has("esito")) {
                switch (response.getJSONObject("esito").getInt("flagEsito")) {
                    case -6:
                        if (response.getJSONObject("esito").getBoolean("captcha")) throw new OpenstudInvalidCredentialsException("Captcha required");
                        else throw new OpenstudInvalidResponseException("Infostud is not working as intended");
                    case -4:
                        throw new OpenstudUserNotEnabledException("User is not enabled to use Infostud service.");
                    case -2:
                        throw new OpenstudInvalidCredentialsException("Password expired").setPasswordExpiredType();
                    case -1:
                        throw new OpenstudInvalidCredentialsException("Password not valid").setPasswordInvalidType();
                    case 0:
                        break;
                    default:
                        throw new OpenstudInvalidResponseException("Infostud is not working as expected");
                }
            } else if(response.has("error")) {
                switch (response.getJSONObject("error").getString("code")) {
                    case "auth110":
                    case "auth500":
                        throw new OpenstudInvalidResponseException("Infostud is not working as intended");
                    case "auth151":
                        throw new OpenstudUserNotEnabledException("User is not enabled to use Infostud service.");
                    case "0":
                        break;
                    default:
                        throw new OpenstudInvalidResponseException("Infostud is not working as expected");
                }
            } else if(token.isEmpty()) {
                throw new OpenstudInvalidResponseException("Infostud is not working as expected");
            }
        } catch (IOException e) {
            OpenstudConnectionException connectionException = new OpenstudConnectionException(e);
            os.log(Level.SEVERE, connectionException);
            throw connectionException;
        } catch (JSONException e) {
            OpenstudInvalidResponseException invalidResponse = new OpenstudInvalidResponseException(e).setJSONType();
            os.log(Level.SEVERE, invalidResponse);
            throw invalidResponse;
        }
        os.setReady(true);
    }
}
