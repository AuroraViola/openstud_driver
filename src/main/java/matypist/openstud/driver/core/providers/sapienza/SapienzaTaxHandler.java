package matypist.openstud.driver.core.providers.sapienza;

import matypist.openstud.driver.core.Openstud;
import matypist.openstud.driver.core.internals.TaxHandler;
import matypist.openstud.driver.core.models.Isee;
import matypist.openstud.driver.core.models.Tax;
import matypist.openstud.driver.exceptions.OpenstudConnectionException;
import matypist.openstud.driver.exceptions.OpenstudInvalidCredentialsException;
import matypist.openstud.driver.exceptions.OpenstudInvalidResponseException;
import matypist.openstud.driver.exceptions.OpenstudRefreshException;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.LocalDate;
import org.threeten.bp.format.DateTimeFormatter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

public class SapienzaTaxHandler implements TaxHandler {
    private Openstud os;

    public SapienzaTaxHandler(Openstud os) {
        this.os = os;
    }

    @Override
    public List<Tax> getPaidTaxes() throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException {
        if (!os.isReady()) return null;
        int count = 0;
        List<Tax> taxes;
        while (true) {
            try {
                if (count > 0) os.refreshToken();
                taxes = _getTaxes(true);
                break;
            } catch (OpenstudInvalidResponseException e) {
                if (e.isMaintenance()) throw e;
                if (++count == os.getMaxTries()) {
                    os.log(Level.SEVERE, e);
                    throw e;
                }
            } catch (OpenstudRefreshException e) {
                OpenstudInvalidCredentialsException invalidCredentials = new OpenstudInvalidCredentialsException(e);
                os.log(Level.SEVERE, invalidCredentials);
                throw invalidCredentials;
            }
        }
        return taxes;
    }

    @Override
    public byte[] getPaymentSlipPDF(Tax unpaidTax) throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException {
        if (!os.isReady() || unpaidTax == null) return null;
        int count = 0;
        byte[] pdf;
        while (true) {
            try {
                if (count > 0) os.refreshToken();
                pdf = _getPaymentSlip(unpaidTax);
                break;
            } catch (OpenstudInvalidResponseException e) {
                if (e.isMaintenance()) throw e;
                if (++count == os.getMaxTries()) {
                    os.log(Level.SEVERE, e);
                    throw e;
                }
            } catch (OpenstudRefreshException e) {
                OpenstudInvalidCredentialsException invalidCredentials = new OpenstudInvalidCredentialsException(e);
                os.log(Level.SEVERE, invalidCredentials);
                throw invalidCredentials;
            }
        }
        return pdf;
    }

    private byte[] _getPaymentSlip(Tax unpaidTax) throws OpenstudInvalidResponseException, OpenstudConnectionException {
        try {
            Request req = new Request.Builder().url(String.format("%s/contabilita/%s/%s/ristampa?ingresso=%s", os.getEndpointAPI(), os.getStudentID(), unpaidTax.getCode(), os.getToken())).build();
            Response resp = os.getClient().newCall(req).execute();
            if (resp.body() == null) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            String body = resp.body().string();
            resp.close();
            os.log(Level.INFO, body);
            JSONObject response = new JSONObject(body);
            if (!response.has("risultato") || response.isNull("risultato"))
                throw new OpenstudInvalidResponseException("Infostud answer is not valid, maybe the token is no longer valid");
            response = response.getJSONObject("risultato");
            if (!response.has("byte") || response.isNull("byte"))
                throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            JSONArray byteArray = response.getJSONArray("byte");
            byte[] pdf = new byte[byteArray.length()];
            for (int i = 0; i < byteArray.length(); i++) pdf[i] = (byte) byteArray.getInt(i);
            os.log(Level.INFO, "Found PDF made of " + pdf.length + " bytes \n");
            return pdf;
        } catch (IOException e) {
            OpenstudConnectionException connectionException = new OpenstudConnectionException(e);
            os.log(Level.SEVERE, connectionException);
            throw connectionException;
        } catch (JSONException e) {
            OpenstudInvalidResponseException invalidResponse = new OpenstudInvalidResponseException(e).setJSONType();
            os.log(Level.SEVERE, invalidResponse);
            throw invalidResponse;
        }
    }


    @Override
    public List<Tax> getUnpaidTaxes() throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException {
        if (!os.isReady()) return null;
        int count = 0;
        List<Tax> taxes;
        while (true) {
            try {
                if (count > 0) os.refreshToken();
                taxes = _getTaxes(false);
                break;
            } catch (OpenstudInvalidResponseException e) {
                if (e.isMaintenance()) throw e;
                if (++count == os.getMaxTries()) {
                    os.log(Level.SEVERE, e);
                    throw e;
                }
            } catch (OpenstudRefreshException e) {
                OpenstudInvalidCredentialsException invalidCredentials = new OpenstudInvalidCredentialsException(e);
                os.log(Level.SEVERE, invalidCredentials);
                throw invalidCredentials;
            }
        }
        return taxes;
    }

    private List<Tax> _getTaxes(boolean paid) throws OpenstudConnectionException, OpenstudInvalidResponseException {
        try {
            String partial;
            if (paid) partial = "bollettinipagati";
            else partial = "bollettininonpagati";
            Request req = new Request.Builder().url(String.format("%s/contabilita/%s/%s?ingresso=%s", os.getEndpointAPI(), os.getStudentID(), partial, os.getToken())).build();
            Response resp = os.getClient().newCall(req).execute();
            List<Tax> list = new LinkedList<>();
            if (resp.body() == null) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            String body = resp.body().string();
            resp.close();
            os.log(Level.INFO, body);
            JSONObject response = new JSONObject(body);
            if (!response.has("risultatoLista"))
                throw new OpenstudInvalidResponseException("Infostud response is not valid. I guess the token is no longer valid");
            if (response.isNull("risultatoLista"))
                return new LinkedList<>();
            response = response.getJSONObject("risultatoLista");
            if (!response.has("risultati") || response.isNull("risultati")) return new LinkedList<>();
            JSONArray array = response.getJSONArray("risultati");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Tax tax = new Tax();
                for (String element : obj.keySet()) {
                    switch (element) {
                        case "codiceBollettino":
                            tax.setCode(obj.getString(element));
                            break;
                        case "corsoDiStudi":
                            tax.setCodeCourse(obj.getString(element));
                            break;
                        case "descCorsoDiStudi":
                            tax.setDescriptionCourse(obj.getString(element));
                            break;
                        case "impoVers":
                            try {
                                String content = obj.getString(element);
                                if (!content.isEmpty()) tax.setAmount(Double.parseDouble(obj.getString(element)));
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                                os.log(Level.SEVERE, e);
                            }
                            break;
                        case "annoAcca":
                            tax.setAcademicYear(obj.getInt(element));
                            break;
                        case "dataVers":
                            if (!paid) break;
                            tax.setPaymentDate(LocalDate.parse(obj.getString(element), formatter));
                            break;
                        case "importoBollettino":
                            if (obj.isNull(element)) break;
                            try {
                                double value = Double.parseDouble(obj.getString(element).replace(",", "."));
                                tax.setAmount(value);
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                                os.log(Level.SEVERE, e);
                            }
                            break;
                        case "scadenza":
                            if (obj.getString(element).isEmpty()) continue;
                            tax.setExpirationDate(LocalDate.parse(obj.getString(element), formatter));
                            break;
                        default:
                            break;
                    }
                }
                tax.setPaymentDescriptionList(SapienzaHelper.extractPaymentDescriptionList(os, obj.getJSONArray("causali")));
                if (paid) tax.setStatus(Tax.TaxStatus.PAID);
                else tax.setStatus(Tax.TaxStatus.UNPAID);
                list.add(tax);
            }
            return list;
        } catch (IOException e) {
            OpenstudConnectionException connectionException = new OpenstudConnectionException(e);
            os.log(Level.SEVERE, connectionException);
            throw connectionException;
        } catch (JSONException e) {
            OpenstudInvalidResponseException invalidResponse = new OpenstudInvalidResponseException(e).setJSONType();
            os.log(Level.SEVERE, invalidResponse);
            throw invalidResponse;
        }
    }

    public Isee getCurrentIsee() throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException {
        if (!os.isReady()) return null;
        int count = 0;
        Isee isee;
        while (true) {
            try {
                if (count > 0) os.refreshToken();
                isee = _getCurrentIsee();
                break;
            } catch (OpenstudInvalidResponseException e) {
                if (e.isMaintenance()) throw e;
                if (++count == os.getMaxTries()) {
                    os.log(Level.SEVERE, e);
                    throw e;
                }
            } catch (OpenstudRefreshException e) {
                OpenstudInvalidCredentialsException invalidCredentials = new OpenstudInvalidCredentialsException(e);
                os.log(Level.SEVERE, invalidCredentials);
                throw invalidCredentials;
            }
        }
        return isee;
    }

    private Isee _getCurrentIsee() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        try {
            Request req = new Request.Builder().url(String.format("%s/contabilita/%s/isee?ingresso=%s", os.getEndpointAPI(), os.getStudentID(), os.getToken())).build();
            Response resp = os.getClient().newCall(req).execute();
            if (resp.body() == null) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            String body = resp.body().string();
            resp.close();
            os.log(Level.INFO, body);
            JSONObject response = new JSONObject(body);
            if (!response.has("risultato"))
                throw new OpenstudInvalidResponseException("Infostud response is not valid. I guess the token is no longer valid");
            response = response.getJSONObject("risultato");
            return SapienzaHelper.extractIsee(os, response);
        } catch (IOException e) {
            os.log(Level.SEVERE, e);
            throw new OpenstudConnectionException(e);
        } catch (JSONException e) {
            OpenstudInvalidResponseException invalidResponse = new OpenstudInvalidResponseException(e).setJSONType();
            os.log(Level.SEVERE, invalidResponse);
            throw invalidResponse;
        }
    }

    public List<Isee> getIseeHistory() throws OpenstudConnectionException, OpenstudInvalidResponseException, OpenstudInvalidCredentialsException {
        if (!os.isReady()) return null;
        int count = 0;
        List<Isee> history;
        while (true) {
            try {
                if (count > 0) os.refreshToken();
                history = _getIseeHistory();
                break;
            } catch (OpenstudInvalidResponseException e) {
                if (e.isMaintenance()) throw e;
                if (++count == os.getMaxTries()) {
                    os.log(Level.SEVERE, e);
                    throw e;
                }
            } catch (OpenstudRefreshException e) {
                OpenstudInvalidCredentialsException invalidCredentials = new OpenstudInvalidCredentialsException(e);
                os.log(Level.SEVERE, invalidCredentials);
                throw invalidCredentials;
            }
        }
        return history;
    }

    private List<Isee> _getIseeHistory() throws OpenstudConnectionException, OpenstudInvalidResponseException {
        try {
            Request req = new Request.Builder().url(String.format("%s/contabilita/%s/listaIsee?ingresso=%s", os.getEndpointAPI(), os.getStudentID(), os.getToken())).build();
            Response resp = os.getClient().newCall(req).execute();
            List<Isee> list = new LinkedList<>();
            if (resp.body() == null) throw new OpenstudInvalidResponseException("Infostud answer is not valid");
            String body = resp.body().string();
            resp.close();
            os.log(Level.INFO, body);
            JSONObject response = new JSONObject(body);
            if (!response.has("risultatoLista"))
                throw new OpenstudInvalidResponseException("Infostud response is not valid. I guess the token is no longer valid");
            response = response.getJSONObject("risultatoLista");
            if (!response.has("risultati") || response.isNull("risultati")) return new LinkedList<>();
            JSONArray array = response.getJSONArray("risultati");
            for (int i = 0; i < array.length(); i++) {
                Isee result = SapienzaHelper.extractIsee(os, array.getJSONObject(i));
                if (result == null) continue;
                list.add(SapienzaHelper.extractIsee(os, array.getJSONObject(i)));
            }
            return list;
        } catch (IOException e) {
            OpenstudConnectionException connectionException = new OpenstudConnectionException(e);
            os.log(Level.SEVERE, connectionException);
            throw connectionException;
        } catch (JSONException e) {
            OpenstudInvalidResponseException invalidResponse = new OpenstudInvalidResponseException(e).setJSONType();
            os.log(Level.SEVERE, invalidResponse);
            throw invalidResponse;
        }
    }
}
