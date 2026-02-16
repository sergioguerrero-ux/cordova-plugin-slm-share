var exec = require('cordova/exec');

var SLMShare = {

    /**
     * Abre el share sheet nativo del OS.
     * @param {Object} options - Opciones de compartir
     *   {
     *     text: string,       // texto a compartir
     *     url: string,        // URL a compartir
     *     image: string,      // base64 de imagen (opcional)
     *     title: string       // titulo para el share sheet (opcional)
     *   }
     * @param {Function} successCallback - Recibe { completed, app? }
     * @param {Function} errorCallback - Recibe string con mensaje de error
     */
    share: function (options, successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'SLMShare', 'share', [options || {}]);
    },

    /**
     * Comparte directamente a una app especifica.
     * @param {Object} options - Opciones de compartir
     *   {
     *     app: "whatsapp"|"telegram"|"instagram"|"facebook"|"twitter"|"email"|"sms",
     *     text: string,
     *     url: string,
     *     image: string,      // base64 (opcional)
     *     phoneNumber: string  // para whatsapp/sms (opcional)
     *   }
     * @param {Function} successCallback - Recibe { completed, app }
     * @param {Function} errorCallback - Recibe string con mensaje de error
     */
    shareToApp: function (options, successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'SLMShare', 'shareToApp', [options || {}]);
    },

    /**
     * Detecta que apps de redes sociales estan instaladas.
     * @param {Function} successCallback - Recibe { whatsapp, telegram, instagram, facebook, twitter, email, sms }
     * @param {Function} errorCallback - Recibe string con mensaje de error
     */
    getAvailableApps: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'SLMShare', 'getAvailableApps', []);
    },

    /**
     * Captura screenshot del webview y opcionalmente comparte.
     * @param {Object} options - Opciones
     *   {
     *     share: boolean,     // si abrir share sheet despues de captura (default false)
     *     returnBase64: boolean // si retornar el base64 (default true)
     *   }
     * @param {Function} successCallback - Recibe { completed, base64? }
     * @param {Function} errorCallback - Recibe string con mensaje de error
     */
    shareScreenshot: function (options, successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'SLMShare', 'shareScreenshot', [options || {}]);
    },

    /**
     * Guarda una imagen base64 en la galeria del dispositivo.
     * @param {string} base64 - Imagen en base64
     * @param {Function} successCallback - Recibe { saved, path? }
     * @param {Function} errorCallback - Recibe string con mensaje de error
     */
    saveToGallery: function (base64, successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'SLMShare', 'saveToGallery', [base64 || ""]);
    }
};

module.exports = SLMShare;
