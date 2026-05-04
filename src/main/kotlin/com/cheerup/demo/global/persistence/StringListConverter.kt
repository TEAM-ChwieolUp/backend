package com.cheerup.demo.global.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StringListConverter : AttributeConverter<MutableList<String>, String> {

    override fun convertToDatabaseColumn(attribute: MutableList<String>?): String =
        MAPPER.writeValueAsString(attribute ?: mutableListOf<String>())

    override fun convertToEntityAttribute(dbData: String?): MutableList<String> {
        if (dbData.isNullOrBlank()) return mutableListOf()
        return MAPPER.readValue(dbData, TYPE_REF)
    }

    companion object {
        private val MAPPER: ObjectMapper = jacksonObjectMapper()
        private val TYPE_REF = object : TypeReference<MutableList<String>>() {}
    }
}
