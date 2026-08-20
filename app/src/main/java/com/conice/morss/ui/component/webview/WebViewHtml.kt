package com.conice.morss.ui.component.webview

object WebViewHtml {

    const val HTML: String = """
<!DOCTYPE html>
<html dir="auto">
<head>
    <meta name="viewport" content="initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no, width=device-width, viewport-fit=cover" />
    <meta content="text/html; charset=utf-8" http-equiv="content-type"/>
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https: data: blob:; media-src http: https: data: blob:; style-src 'unsafe-inline'; font-src data: file:; script-src 'unsafe-inline'; connect-src 'none'; object-src 'none'; frame-src http://youtube.com https://youtube.com http://*.youtube.com https://*.youtube.com http://youtube-nocookie.com https://youtube-nocookie.com http://*.youtube-nocookie.com https://*.youtube-nocookie.com http://player.vimeo.com https://player.vimeo.com http://player.bilibili.com https://player.bilibili.com; child-src http://youtube.com https://youtube.com http://*.youtube.com https://*.youtube.com http://youtube-nocookie.com https://youtube-nocookie.com http://*.youtube-nocookie.com https://*.youtube-nocookie.com http://player.vimeo.com https://player.vimeo.com http://player.bilibili.com https://player.bilibili.com; form-action 'none'; base-uri http: https:" />
    <style type="text/css">
        %s
    </style>
    <base href="%s" />
</head>
<body>
<main>
    <article>
        %s
    </article>
</main>
<script>
%s
</script>
</body>
</html>
"""
}
