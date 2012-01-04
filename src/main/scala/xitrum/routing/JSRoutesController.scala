package xitrum.routing

import io.netty.handler.codec.http.{HttpHeaders, HttpResponseStatus}
import HttpHeaders.Names._
import HttpHeaders.Values._
import HttpResponseStatus._

import xitrum.{Controller, Config}
import xitrum.comet.CometGetController
import xitrum.etag.{Etag, NotModified}
import xitrum.util.Gzip

object JSRoutesController extends JSRoutesController {
  private var js:        String      = null
  private var gzippedJs: Array[Byte] = null

  // This value is stable, even across different servers in a cluster
  private lazy val etag = Etag.forString(Routes.jsRoutes)

  def jsRoutes(controller: Controller) = synchronized {
    if (js == null) {
      js =
        "var XITRUM_BASE_URI = '" + Config.baseUri + "';\n" +
        "var XITRUM_ROUTES = " + Routes.jsRoutes + ";\n" +
        "var XITRUM_COMET_GET_ACTION = '" + CometGetController.postback.postbackUrl + "';"
    }
    js
  }

  def gzippedJsRoutes(controller: Controller): Array[Byte] = synchronized {
    if (gzippedJs == null) {
      gzippedJs = Gzip.compress(jsRoutes(controller).getBytes(Config.config.request.charset))
    }
    gzippedJs
  }
}

class JSRoutesController extends Controller {
  import JSRoutesController._

  val serve: Route = GET("xitrum/routes.js") {
    if (!Etag.respondIfEtagsIdentical(this, etag)) {
      NotModified.setClientCacheAggressively(response)
      response.setHeader(CONTENT_TYPE, "text/javascript")
      if (Gzip.isAccepted(request)) {
        response.setHeader(CONTENT_ENCODING, "gzip")
        respondBinary(gzippedJsRoutes(this))
      } else {
        jsRespond(jsRoutes(this))
      }
    }
  }
}