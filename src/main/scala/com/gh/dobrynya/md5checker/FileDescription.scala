package com.gh.dobrynya.md5checker

case object FileDescription {
  private val pattern = "(.+),(.+)".r

  def of(raw: String) = raw match {
    case pattern(url, md5) => FileDescription(url, md5)
    case _ => FileDescription("", "", error = Some(s"Invalid data: $raw!"))
  }
}

case class FileDescription(url: String, md5: String, calculatedMd5: Option[String] = None, error: Option[String] = None) {
  def valid: Boolean = error.isEmpty
}
