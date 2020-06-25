package net.corda.plugins

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Represent
import org.yaml.snakeyaml.representer.Representer
import java.io.InputStream

class DockerformUtils {

    companion object {

        private val YAML_FORMAT_OPTIONS = DumperOptions().apply {
            indent = 2
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        }

        private val YAML_MAPPER = Yaml(DockerComposeRepresenter(), YAML_FORMAT_OPTIONS)

        fun dump(data: Any): String =  YAML_MAPPER.dump(data)

        fun load(io: InputStream): Map<Any, Any> = YAML_MAPPER.load(io)
    }

    internal class QuotedString(var value: String)

    internal class DockerComposeRepresenter : Representer() {

        private inner class RepresentQuotedString : Represent {
            override fun representData(data: Any): org.yaml.snakeyaml.nodes.Node? {
                val str = data as QuotedString
                return representScalar(Tag.STR, str.value, DumperOptions.ScalarStyle.DOUBLE_QUOTED.char)
            }
        }

        init {
            this.representers[QuotedString::class.java] = RepresentQuotedString()
        }
    }
}