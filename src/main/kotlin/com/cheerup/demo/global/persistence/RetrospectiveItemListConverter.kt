package com.cheerup.demo.global.persistence

import com.cheerup.demo.retrospective.domain.RetrospectiveItem
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class RetrospectiveItemListConverter : AttributeConverter<MutableList<RetrospectiveItem>, String> {

    override fun convertToDatabaseColumn(attribute: MutableList<RetrospectiveItem>?): String =
        MAPPER.writeValueAsString(attribute ?: mutableListOf<RetrospectiveItem>())

    override fun convertToEntityAttribute(dbData: String?): MutableList<RetrospectiveItem> {
        if (dbData.isNullOrBlank()) return mutableListOf()
        return MAPPER.readValue(dbData, TYPE_REF)
    }

    companion object {
        private val MAPPER: ObjectMapper = jacksonObjectMapper()
        private val TYPE_REF = object : TypeReference<MutableList<RetrospectiveItem>>() {}
    }
}
