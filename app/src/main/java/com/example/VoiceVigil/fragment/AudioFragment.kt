package com.example.VoiceVigil.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.VoiceVigil.ProjectConfiguration
import com.example.VoiceVigil.audioInference.SoundClassifier
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import com.example.VoiceVigil.databinding.FragmentAudioBinding // fragment_audio.xml

class AudioFragment: Fragment(), RecognitionListener {
    private val TAG = "AudioFragment"

    private var _fragmentAudioBinding: FragmentAudioBinding? = null

    private val fragmentAudioBinding
        get() = _fragmentAudioBinding!!

    // classifiers
    lateinit var soundClassifier: SoundClassifier

    // views
    lateinit var keyWordView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentAudioBinding = FragmentAudioBinding.inflate(inflater, container, false)

        return fragmentAudioBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        keyWordView = fragmentAudioBinding.keyWordView

        soundClassifier = SoundClassifier(requireContext())
        soundClassifier.setDetectorListener(this as RecognitionListener)
        soundClassifier.initialize()
    }

    override fun onPause() {
        super.onPause()
//        snapClassifier.stopInferencing()
    }

    override fun onResume() {
        super.onResume()
//        snapClassifier.startInferencing()
    }

    // listener functions
    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {}

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onError(error: Int) {
        soundClassifier.handleRecognitionError(error)
    }

    override fun onResults(results: Bundle?) {
        soundClassifier.handleRecognitionResults(results)
        activity?.runOnUiThread {
            keyWordView.text =
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
        }
//            if (results) {
//                keyWordView.text = "SNAP"
//                keyWordView.setBackgroundColor(ProjectConfiguration.activeBackgroundColor)
//                keyWordView.setTextColor(ProjectConfiguration.activeTextColor)
//            } else {
//                keyWordView.text = "NO SNAP"
//                keyWordView.setBackgroundColor(ProjectConfiguration.idleBackgroundColor)
//                keyWordView.setTextColor(ProjectConfiguration.idleTextColor)
//            }
//        }
    }


//    fun onResults(score: Float) {
//        activity?.runOnUiThread {
//            if (score > SnapClassifier.THRESHOLD) {
//                keyWordView.text = "SNAP"
//                keyWordView.setBackgroundColor(ProjectConfiguration.activeBackgroundColor)
//                keyWordView.setTextColor(ProjectConfiguration.activeTextColor)
//            } else {
//                keyWordView.text = "NO SNAP"
//                keyWordView.setBackgroundColor(ProjectConfiguration.idleBackgroundColor)
//                keyWordView.setTextColor(ProjectConfiguration.idleTextColor)
//            }
//        }
//    }
}