import UIKit
import Photos
import WebKit

@objc(SLMShare) class SLMShare: CDVPlugin {

    // MARK: - share

    @objc(share:)
    func share(command: CDVInvokedUrlCommand) {
        let options = command.argument(at: 0) as? [String: Any] ?? [:]
        let text = options["text"] as? String
        let urlString = options["url"] as? String
        let imageBase64 = options["image"] as? String

        var activityItems: [Any] = []

        if let text = text { activityItems.append(text) }
        if let urlString = urlString, let url = URL(string: urlString) { activityItems.append(url) }
        if let imageBase64 = imageBase64,
           let imageData = Data(base64Encoded: imageBase64),
           let image = UIImage(data: imageData) {
            activityItems.append(image)
        }

        if activityItems.isEmpty {
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "No hay contenido para compartir")
            commandDelegate.send(result, callbackId: command.callbackId)
            return
        }

        DispatchQueue.main.async {
            let activityVC = UIActivityViewController(activityItems: activityItems, applicationActivities: nil)

            activityVC.completionWithItemsHandler = { activityType, completed, _, error in
                if let error = error {
                    let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error.localizedDescription)
                    self.commandDelegate.send(result, callbackId: command.callbackId)
                    return
                }

                var info: [String: Any] = ["completed": completed]
                if let activityType = activityType {
                    info["app"] = activityType.rawValue
                }
                let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: info)
                self.commandDelegate.send(result, callbackId: command.callbackId)
            }

            // iPad support
            if let popover = activityVC.popoverPresentationController {
                popover.sourceView = self.viewController.view
                popover.sourceRect = CGRect(x: self.viewController.view.bounds.midX, y: self.viewController.view.bounds.midY, width: 0, height: 0)
                popover.permittedArrowDirections = []
            }

            self.viewController.present(activityVC, animated: true)
        }
    }

    // MARK: - shareToApp

    @objc(shareToApp:)
    func shareToApp(command: CDVInvokedUrlCommand) {
        let options = command.argument(at: 0) as? [String: Any] ?? [:]
        let app = options["app"] as? String ?? ""
        let text = options["text"] as? String ?? ""
        let urlString = options["url"] as? String
        let imageBase64 = options["image"] as? String
        let phoneNumber = options["phoneNumber"] as? String

        DispatchQueue.main.async {
            var opened = false

            switch app {
            case "whatsapp":
                var whatsappURL = "whatsapp://send?"
                if let phone = phoneNumber {
                    whatsappURL += "phone=\(phone)&"
                }
                whatsappURL += "text=\(text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? text)"
                if let url = urlString {
                    whatsappURL += "%20\(url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? url)"
                }
                opened = self.openURL(whatsappURL)

            case "telegram":
                var tgURL = "tg://msg?text=\(text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? text)"
                if let url = urlString {
                    tgURL += "%20\(url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? url)"
                }
                opened = self.openURL(tgURL)

            case "instagram":
                if let imageBase64 = imageBase64,
                   let imageData = Data(base64Encoded: imageBase64) {
                    // Instagram Stories API via UIPasteboard
                    let pasteboardItems: [[String: Any]] = [
                        ["com.instagram.sharedSticker.backgroundImage": imageData]
                    ]
                    let options: [UIPasteboard.OptionsKey: Any] = [
                        .expirationDate: Date().addingTimeInterval(60 * 5)
                    ]
                    UIPasteboard.general.setItems(pasteboardItems, options: options)
                    opened = self.openURL("instagram-stories://share")
                } else {
                    opened = self.openURL("instagram://app")
                }

            case "facebook":
                if let url = urlString {
                    opened = self.openURL("fb://share?link=\(url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? url)")
                } else {
                    opened = self.openURL("fb://")
                }

            case "twitter":
                var twitterURL = "twitter://post?message=\(text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? text)"
                if let url = urlString {
                    twitterURL += "%20\(url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? url)"
                }
                // Try x:// first, then twitter://
                if !self.openURL("x://post?text=\(text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? text)") {
                    opened = self.openURL(twitterURL)
                } else {
                    opened = true
                }

            case "email":
                var emailURL = "mailto:?"
                emailURL += "body=\(text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? text)"
                if let url = urlString {
                    emailURL += "%20\(url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? url)"
                }
                opened = self.openURL(emailURL)

            case "sms":
                var smsURL = "sms:"
                if let phone = phoneNumber {
                    smsURL += phone
                }
                smsURL += "&body=\(text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? text)"
                opened = self.openURL(smsURL)

            default:
                let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "App no soportada: \(app)")
                self.commandDelegate.send(result, callbackId: command.callbackId)
                return
            }

            if opened {
                let info: [String: Any] = ["completed": true, "app": app]
                let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: info)
                self.commandDelegate.send(result, callbackId: command.callbackId)
            } else {
                let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "\(app) no esta instalada o no se pudo abrir")
                self.commandDelegate.send(result, callbackId: command.callbackId)
            }
        }
    }

    // MARK: - getAvailableApps

    @objc(getAvailableApps:)
    func getAvailableApps(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            let apps: [String: Any] = [
                "whatsapp": self.canOpenURL("whatsapp://"),
                "telegram": self.canOpenURL("tg://"),
                "instagram": self.canOpenURL("instagram://"),
                "facebook": self.canOpenURL("fb://"),
                "twitter": self.canOpenURL("twitter://") || self.canOpenURL("x://"),
                "email": self.canOpenURL("mailto:"),
                "sms": self.canOpenURL("sms:")
            ]
            let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: apps)
            self.commandDelegate.send(result, callbackId: command.callbackId)
        }
    }

    // MARK: - shareScreenshot

    @objc(shareScreenshot:)
    func shareScreenshot(command: CDVInvokedUrlCommand) {
        let options = command.argument(at: 0) as? [String: Any] ?? [:]
        let shouldShare = options["share"] as? Bool ?? false
        let returnBase64 = options["returnBase64"] as? Bool ?? true

        DispatchQueue.main.async {
            guard let webView = self.webView as? WKWebView else {
                // Fallback: capture the main view
                guard let view = self.viewController.view else {
                    let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "No se pudo capturar la pantalla")
                    self.commandDelegate.send(result, callbackId: command.callbackId)
                    return
                }

                let renderer = UIGraphicsImageRenderer(size: view.bounds.size)
                let image = renderer.image { ctx in
                    view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
                }
                self.handleScreenshot(image: image, shouldShare: shouldShare, returnBase64: returnBase64, callbackId: command.callbackId)
                return
            }

            webView.takeSnapshot(with: nil) { image, error in
                if let error = error {
                    let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error.localizedDescription)
                    self.commandDelegate.send(result, callbackId: command.callbackId)
                    return
                }

                guard let image = image else {
                    let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "No se pudo capturar la pantalla")
                    self.commandDelegate.send(result, callbackId: command.callbackId)
                    return
                }

                self.handleScreenshot(image: image, shouldShare: shouldShare, returnBase64: returnBase64, callbackId: command.callbackId)
            }
        }
    }

    private func handleScreenshot(image: UIImage, shouldShare: Bool, returnBase64: Bool, callbackId: String) {
        if shouldShare {
            let activityVC = UIActivityViewController(activityItems: [image], applicationActivities: nil)

            activityVC.completionWithItemsHandler = { _, completed, _, _ in
                var info: [String: Any] = ["completed": completed]
                if returnBase64, let pngData = image.pngData() {
                    info["base64"] = pngData.base64EncodedString()
                }
                let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: info)
                self.commandDelegate.send(result, callbackId: callbackId)
            }

            if let popover = activityVC.popoverPresentationController {
                popover.sourceView = self.viewController.view
                popover.sourceRect = CGRect(x: self.viewController.view.bounds.midX, y: self.viewController.view.bounds.midY, width: 0, height: 0)
                popover.permittedArrowDirections = []
            }

            self.viewController.present(activityVC, animated: true)
        } else {
            var info: [String: Any] = ["completed": true]
            if returnBase64, let pngData = image.pngData() {
                info["base64"] = pngData.base64EncodedString()
            }
            let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: info)
            self.commandDelegate.send(result, callbackId: callbackId)
        }
    }

    // MARK: - saveToGallery

    @objc(saveToGallery:)
    func saveToGallery(command: CDVInvokedUrlCommand) {
        let base64 = command.argument(at: 0) as? String ?? ""

        guard let imageData = Data(base64Encoded: base64),
              let image = UIImage(data: imageData) else {
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Base64 invalido o no es una imagen")
            commandDelegate.send(result, callbackId: command.callbackId)
            return
        }

        PHPhotoLibrary.requestAuthorization { status in
            if status == .authorized || status == .limited {
                PHPhotoLibrary.shared().performChanges({
                    PHAssetChangeRequest.creationRequestForAsset(from: image)
                }) { success, error in
                    if success {
                        let info: [String: Any] = ["saved": true]
                        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: info)
                        self.commandDelegate.send(result, callbackId: command.callbackId)
                    } else {
                        let msg = error?.localizedDescription ?? "Error desconocido al guardar"
                        let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: msg)
                        self.commandDelegate.send(result, callbackId: command.callbackId)
                    }
                }
            } else {
                let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Permiso de galeria denegado")
                self.commandDelegate.send(result, callbackId: command.callbackId)
            }
        }
    }

    // MARK: - Helpers

    @discardableResult
    private func openURL(_ urlString: String) -> Bool {
        guard let url = URL(string: urlString) else { return false }
        if UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
            return true
        }
        return false
    }

    private func canOpenURL(_ urlString: String) -> Bool {
        guard let url = URL(string: urlString) else { return false }
        return UIApplication.shared.canOpenURL(url)
    }
}
