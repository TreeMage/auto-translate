package slack

import sttp.client3.*
import sttp.model.StatusCode
import upickle.default.*
import cats.implicits.*

case class SlackError(message: String)
trait SlackClient:
  def send(message: String): Either[SlackError, Unit]

object SlackClient:
  private case class SlackMessage(text: String) derives Writer
  def make(webHookUrl: String): SlackClient = new SlackClient:
    private val backend = HttpClientSyncBackend()
    override def send(message: String): Either[SlackError, Unit] =
      val request = basicRequest
        .post(uri"$webHookUrl")
        .body(write(SlackMessage(message)))
      val response = request.send(backend)
      if (response.code != StatusCode.Ok)
        return SlackError(response.statusText).asLeft
      ().asRight
