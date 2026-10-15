package com.everettpenne.seekur

import com.everettpenne.seekur.data.media.MediaExtractor
import com.everettpenne.seekur.data.media.Posts
import com.everettpenne.seekur.data.model.*
import org.junit.Assert.*
import org.junit.Test

class MediaExtractorTest {
    private fun post(json: String) = Posts.from(RedditJson.decodeFromString<Thing<PostDto>>(json).data)

    @Test fun anImagePostKeepsItsFieldsAndFindsTheFullSizeImage() {
        val p = post(Fixtures.imagePost)
        assertEquals("A & B", p.title); assertEquals("t3_abc123", p.fullname); assertEquals("r/pics", p.subreddit.displayNamePrefixed)
        assertEquals("https://styles.redditmedia.com/icon.png", p.subreddit.iconUrl)
        assertTrue(p.subreddit.isSubscribed); assertEquals(0xFF0079D3.toInt(), p.subreddit.keyColor)
        assertEquals("Photo", p.linkFlairText); assertEquals(0xFFFF4500.toInt(), p.flairBackground); assertTrue(p.flairTextLight)
        val image = (p.media as MediaType.Image).image
        assertEquals("https://i.redd.it/xyz.jpg?width=640&s=sig", image.url.replace("/preview.", "/i."))
        assertEquals(640, image.width); assertEquals(480, image.height)
    }

    @Test fun aSelfPostHasNoMedia() {
        val p = post(Fixtures.selfPost)
        assertEquals(MediaType.None, p.media); assertEquals("Why **is** the sky blue?", p.selftext)
    }

    @Test fun aGalleryUsesTheOriginalsAndSkipsFailedItems() {
        val gallery = post(Fixtures.galleryPost).media as MediaType.Gallery
        assertEquals(listOf("https://i.redd.it/m1.jpg", "https://i.redd.it/m3.png"), gallery.images.map { it.url })
        assertEquals(800, gallery.images[0].width)
        assertEquals(.5f, gallery.images[1].aspectRatio!!, 1e-6f)
    }

    @Test fun aRedditVideoUsesItsStreamAndTheNonExternalThumbnailOnly() {
        val v = post(Fixtures.videoPost).media as MediaType.Video
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8", v.url); assertEquals(720, v.width); assertEquals(1280, v.height)
        assertNull("external-preview thumbnails are skipped", v.thumbnailUrl)
    }

    @Test fun aYouTubeLinkIsRecognisedWithItsThumbnail() {
        val y = post(Fixtures.youtubePost).media as MediaType.YouTube
        assertEquals("dQw4w9WgXcQ", y.videoId); assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", y.image.url)
    }

    @Test fun anOrdinaryLinkBecomesALinkCard() {
        val l = (post(Fixtures.linkPost).media as MediaType.Link).metadata
        assertEquals("https://example.com/story", l.url); assertEquals("example.com", l.domain)
        assertEquals("https://external-preview.redd.it/t.jpg", l.thumbnailUrl)
    }

    @Test fun aCrosspostShowsTheOriginal() {
        val r = post(Fixtures.crosspost).media as MediaType.Repost
        assertEquals("Original", r.original.title); assertTrue(r.original.media is MediaType.Image)
    }

    @Test fun youTubeIdsComeFromEveryUrlShape() {
        assertEquals("abc", MediaExtractor.youTubeId("https://youtu.be/abc?t=3"))
        assertEquals("abc", MediaExtractor.youTubeId("https://www.youtube.com/watch?v=abc&x=1"))
        assertEquals("abc", MediaExtractor.youTubeId("https://www.youtube.com/embed/abc"))
        assertNull(MediaExtractor.youTubeId("https://example.com"))
    }

    @Test fun missingFieldsNeverBreakAPost() {
        val p = post("""{"kind":"t3","data":{"id":"x"}}""")
        assertEquals("x", p.id); assertEquals("t3_x", p.fullname); assertEquals(MediaType.None, p.media)
    }
}

class SavedCodecTest {
    private fun post(json: String) = Posts.from(RedditJson.decodeFromString<Thing<PostDto>>(json).data)

    @Test fun savedPostsSurviveAWriteAndReadWithEveryKindOfMedia() {
        val posts = listOf(Fixtures.imagePost, Fixtures.selfPost, Fixtures.galleryPost, Fixtures.videoPost, Fixtures.youtubePost, Fixtures.linkPost, Fixtures.crosspost).map(::post)
        assertEquals(posts, com.everettpenne.seekur.data.SavedCodec.decode(com.everettpenne.seekur.data.SavedCodec.encode(posts)))
    }

    @Test fun unreadableStorageIsAnEmptyList() {
        assertEquals(emptyList<Post>(), com.everettpenne.seekur.data.SavedCodec.decode("garbage"))
        assertEquals(emptyList<Post>(), com.everettpenne.seekur.data.SavedCodec.decode(null))
    }
}
